package bguspl.set.ex;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.print.event.PrintJobListener;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private ArrayBlockingQueue<Integer> myTokens; // store the slots that the playes placed a token on

    private LinkedBlockingQueue<Integer> tokensToPlace; // store the slots that the players !inted! to place a token on
    private final int MAX_WAITING_TOKENS = 32; // use for the AI Thered.

    private LinkedBlockingQueue<Integer> gotResponse; // store the response of the dealer. 1 for leagal set and -1 for
                                                      // illigal set.

    private final int NUM_OF_PRMITED_TOKENS;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        NUM_OF_PRMITED_TOKENS = env.config.featureSize;
        this.myTokens = new ArrayBlockingQueue<>(NUM_OF_PRMITED_TOKENS);
        this.tokensToPlace = new LinkedBlockingQueue<>();
        this.gotResponse = new LinkedBlockingQueue<>(1);
    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) {

            createArtificialIntelligence();
        }

        while (!terminate) {

            try {
                int slotToplaceToken = tokensToPlace.take();

                if (!table.playerHasToken(this.id, slotToplaceToken)) {
                    placeToken(slotToplaceToken);
                } else { // the player alredy has token at this slot
                    removeToken(slotToplaceToken);

                }

            } catch (InterruptedException e) {
                break;
            }

        }

        if (!human) {
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        }

        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        System.out.println("thread " + Thread.currentThread().getName() + " terminated.");

    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                if (tokensToPlace.size() < MAX_WAITING_TOKENS) { // a limit to the nuber of token in the line.
                    int randomKeyPress = new Random().nextInt(env.config.tableSize);
                    this.keyPressed(randomKeyPress);
                }
                try {
                    synchronized (this) {
                        Thread.sleep(2);
                    }
                } catch (InterruptedException ignored) {
                }
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
            System.out.println("ai Thread " + Thread.currentThread().getName() + " terminated.");

        }, "computer-" + id);
        
        
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {

        this.terminate = true; // affect both the playes Thered and the AI Thered if exsit.
        playerThread.interrupt();
        try{
            playerThread.join();
        } catch (InterruptedException ignored){}

    }

    /**
     * This method is called when a key is pressed.
     * The input Thered use this metod.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        tokensToPlace.add(slot);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {

        System.out.println("player " + this.id + ".point");

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests

        env.ui.setScore(this.id, ++score); // incrise the score and update the ui
        env.ui.setFreeze(this.id, env.config.pointFreezeMillis); // set the ui to indicate the player as at freez

        long sleepTime = env.config.pointFreezeMillis;

        while (sleepTime > 0) {
            env.ui.setFreeze(this.id, sleepTime);
            try {
                if (sleepTime < 500) {
                    Thread.sleep(sleepTime);
                    sleepTime = 0;

                } else {
                    Thread.sleep(500);
                    sleepTime = sleepTime - 500;
                }
            } catch (InterruptedException e) {
            }
        }
        env.ui.setFreeze(this.id, 0); // return the name at the display back to black

    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {

        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);
        long sleepTime = env.config.penaltyFreezeMillis;
        while (sleepTime > 0) {
            env.ui.setFreeze(this.id, sleepTime);
            try {
                if (sleepTime < 500) {
                    Thread.sleep(sleepTime);
                } else {
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
            }
            sleepTime = sleepTime - 500;
        }

        env.ui.setFreeze(this.id, 0); // return the name at the display back to black

    }

    public int score() {
        return score;
    }

    /**
     * remove a token from the player's token blocking que.
     * note that the metod remove is a metod of the Queue Interface and not the
     * blocking que, will not block Thereds.
     * 
     * @post - the int of the slot is removed from the que.
     */
    public boolean removeToken(int slot) {
        return (myTokens.remove((Integer) slot) && table.removeToken(this.id, slot));
    }

    /**
     * place the token at the table
     * add the token to the playe's myTokens que
     * update the ui
     * 
     * if myTokens is full the metod submit the set at the table to the delaer to
     * cheak.
     */
    public void placeToken(int slot) {
        if (myTokens.size() == NUM_OF_PRMITED_TOKENS) {
            System.out.println("Player: " + this.id + " Can't put more tokens");
            return;
        }

        if (table.placeToken(this.id, slot)) { // there is card at the slot
            try {
                
                myTokens.put(slot);
                env.ui.placeToken(this.id, slot);

                if (myTokens.size() == env.config.featureSize) { // cheak to set
                    // send to dealer
                    table.acceptSetToCheck(myTokens.stream().mapToInt(Integer::intValue).toArray(), getId());
                    // wait for an answer
                    Integer response = gotResponse.take();
                    if (response == 1) {
                        point();
                    } else if (response == -1) {
                        penalty();
                    }
                    // if the answer is not that it means the set was remove by the dealer because
                    // one of the cards was removed
                    if(response!= 1 || response!=-1) {
                        System.out.println("Player: " + this.id + " your set was removed by the dealer.");
                        return;
                    }

                    myTokens.clear(); // remove the tokens
                    

                }
            
            } catch (InterruptedException e) {
            }
        
        } else { // no card at the slot
            System.out.println("Player: " + this.id + " Can't place token on enpty slot");
        }

    }

    public int getId() {
        return this.id;
    }

    public void accepetResponde(int response) {
        gotResponse.add(response);
    }

    public Thread gettThread() {
        return this.playerThread;
    }

}
