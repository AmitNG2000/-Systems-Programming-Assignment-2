package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)


    protected boolean[][] slotToTokens; //2D array so that slotToTokens[i][j] contain true iff at the slot i that player j put a token.

    protected LinkedBlockingQueue<setSlotsAndPlayerId> setsToCheck; //sets for the dealer to check

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.slotToTokens=new boolean[env.config.tableSize][env.config.players]; //At java, by default all elements are initialized to false. 
        this.setsToCheck = new LinkedBlockingQueue<>();
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     * 
     * @pre - the slot is empty
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {


        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        synchronized(slotToTokens[slot]) { //lock the specific slot - partly sync

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        env.ui.placeCard(card,slot);
        }
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     * 
     * @post the card and the assoeted tokens are removed from the table's grid slot. not dealing with the que of the players.
     *      
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        synchronized(slotToTokens[slot]) { //lock the specific slot - partly sync

        for (int playerId = 0; playerId < env.config.players; playerId++) { //cheak if there are tokens (on the grid slot only) and if so, remove them
            if (slotToTokens[slot][playerId]) {
                removeToken(playerId, slot);
            }
        }

        int card =  slotToCard[slot];
        cardToSlot[card] = null;
        slotToCard[slot] = null;

        env.ui.removeCard(slot);
        }
    }

    /**
     * Attempts tp Places a player token on a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     * 
     * @return false if the slot is empty and it is not possible to place the token,
     *         true if the token was placed successfully
     */
    public boolean placeToken(int player, int slot) {

        synchronized(slotToTokens[slot]) { //lock the specific slot - partly sync
                        
            if (slotToCard[slot]==null) { //no card at the slot
                System.out.print("Player" + player + " try to place a token to empty lot.");
                return false;
            }   
            else {
                slotToTokens[slot][player] = true;
                env.ui.placeToken(player, slot);
                return true;
            }
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {

        synchronized(slotToTokens[slot]) { //lock the specific slot - partly sync

            if(slotToTokens[slot][player]){
                slotToTokens[slot][player] = false;
                env.ui.removeToken(player, slot);
                return true;
            }
            
            return false;
        }
    }


    public boolean playerHasToken (int player, int slot){
        synchronized(slotToTokens[slot]) {
        return (slotToTokens[slot][player]);
        }
    }
 
       
    public void acceptSetToCheck (int[] set, int playerId) {
        setsToCheck.add(new setSlotsAndPlayerId(set,playerId));
    }

    public int numOfSetToCheck () {
        return setsToCheck.size();

    }

    public int[] slotToCards (int[] slots) {
        
        int[] cards = new int[slots.length];
        for (int i=0; i<slots.length ; i++) {
            cards[i] = slotToCard[slots[i]];
        }

        return cards;


    }

    
    
    public class setSlotsAndPlayerId {
        protected int[] setSlots;
        protected int playerId;
    
        public setSlotsAndPlayerId(int[] setSlots, int playerId) {
            this.setSlots = setSlots;
            this.playerId = playerId;
        }
    }
}
