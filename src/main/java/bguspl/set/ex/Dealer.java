package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ex.Table.setSlotsAndPlayerId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;
    private setSlotsAndPlayerId currentSet;
    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {

        try {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");

            // creat and start all the players' Threds
            for (Player currPlayer : players) {
                String name = "PlayerThered " + currPlayer.getId();
                new Thread(currPlayer, name).start();
            }

            // main dealer's loop
            while (!shouldFinish()) {
                placeCardsOnTable();
                timerLoop();
                updateTimerDisplay(true);
                removeAllCardsFromTable();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        close();
        removeAllCardsFromTable();
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {

        System.out.println("Dealer.terminate() preforemed by Thered" + Thread.currentThread().getName());

        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {

        if (currentSet != null) {
            // check if set is legal
            int[] cards = table.slotToCards(currentSet.setSlots);

            // send penalty or punishment to player
            if (env.util.testSet(cards)) { // the set is leigal
                players[currentSet.playerId].accepetResponde(1);
                // remove the carsd that constitute a set
                for (int slot : currentSet.setSlots) {
                    removeCardAndTokens(slot);
                }
                updateTimerDisplay(true);
            } else {
                players[currentSet.playerId].accepetResponde(-1); // the set is illigal
            }


        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        Collections.shuffle(deck);
        for (int slot = 0; slot < table.slotToCard.length; slot++) {

            if (table.slotToCard[slot] == null) { // the slot is empty

                int newCard = deck.remove(0);
                table.placeCard(newCard, slot);
                env.ui.placeCard(newCard, slot);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            if (reshuffleTime - System.currentTimeMillis() > env.config.turnTimeoutWarningMillis)
                currentSet = table.setsToCheck.poll(1000, TimeUnit.MILLISECONDS);
            else
                currentSet = table.setsToCheck.poll(10, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {

        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {

        long timeLeft;

        if (reset || reshuffleTime == Long.MAX_VALUE) {

            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            timeLeft = reshuffleTime;
        } else {
            timeLeft = reshuffleTime - System.currentTimeMillis();
        }
        timeLeft = Math.max(0, timeLeft); // stay positive :)
        boolean warningTimeZone = (timeLeft <= env.config.turnTimeoutWarningMillis);

        env.ui.setCountdown(timeLeft, warningTimeZone);

    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {

        for (int slot = 0; slot < env.config.tableSize; slot++) {
            if (table.slotToCard[slot] != null) {
                deck.add(table.slotToCard[slot]);
                removeCardAndTokens(slot);
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = 0;
        for (Player currPlayer : players) {
            maxScore = Math.max(maxScore, currPlayer.score());
        }
        ArrayList<Integer> winnersIds = new ArrayList<>();
        for (Player currPlayer : players) {
            if (currPlayer.score() == maxScore) {
                winnersIds.add(currPlayer.getId());
            }
        }
        int[] winnersIdArrray = winnersIds.stream().mapToInt(Integer::intValue).toArray();
        env.ui.announceWinner(winnersIdArrray);

        try {
            Thread.sleep(env.config.endGamePauseMillies);
        } catch (Exception e) {
        }
    }

    /**
     * @param slot
     * @post the card and tokens is removed from the table's grid and from the
     *       players' tokens' 'que.
     */
    private void removeCardAndTokens(int slot) {
        for (int playerId = 0; playerId < env.config.players; playerId++) {
            if (table.playerHasToken(playerId, slot)) {
                players[playerId].removeToken(slot);
            }
        }
        table.removeCard(slot);

    }

    public void close() {
        // terminate all the players
        for (int i = players.length - 1; i >= 0; i--) {
            Player currentPlayer = players[i];
            currentPlayer.terminate();

            try {
                Thread playThread = currentPlayer.gettThread();
                System.out.println(
                        "Thered" + Thread.currentThread().getName() + " preforem join() on" + playThread.getName());
                playThread.join();
            } catch (InterruptedException e) {
            }

        } // end of for loop

    }

}
