package bguspl.set.ex;

import bguspl.set.Env;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
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

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    public BlockingQueue<Integer> dealerQueue;
    Thread[] playersThreads;
    Thread dealerThread;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        playersThreads = new Thread[players.length];
        dealerQueue = new ArrayBlockingQueue<Integer>(players.length, true);
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for(int i =0 ; i<players.length;i++){
            playersThreads[i] = new Thread(players[i]);
            playersThreads[i].start();
            dealerThread=Thread.currentThread();
        }
        while (!shouldFinish()) {
            Collections.shuffle(deck);
            placeCardsOnTable();
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            updateTimerDisplay(false);
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
            // placeCardsOnTable();
            // timerLoop();
            // updateTimerDisplay(false);
            // removeAllCardsFromTable();
        }
        if(!terminate) terminate();
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
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
        // TODO implement
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
        if(!dealerQueue.isEmpty()){
            int playerId= dealerQueue.remove();
            int[] checkSet=table.playerChosenCards(playerId);
            if(checkSet.length==env.config.featureSize){ 
               if(env.util.testSet(checkSet)){
                    for(int card:checkSet){
                        int slot=table.cardToSlot[card];
                        Set<Integer> currSet = table.playersPerSlot.get(slot);
                        for(int id: currSet){
                            players[id].decreaseToken();
                        }
                        table.removeCard(slot);                  
                    }
                    players[playerId].point();
                    reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
                }
                else{
                players[playerId].penalty();
                }
            }   
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        List<Integer> places = new ArrayList<>();
        for(int j = 0; j<env.config.tableSize;j++)
            places.add(j);
        Collections.shuffle(places);
        if(table.countCards() != env.config.tableSize && !deck.isEmpty()){
            for(int i =0 ;i < env.config.tableSize ;i++){
                if(table.slotToCard[places.get(i)] == null){
                    if(!deck.isEmpty())
                        table.placeCard(deck.remove(0), places.get(i)); 
                }                
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        List<Integer> places = new ArrayList<>();
        for(int j = 0; j<env.config.tableSize;j++)
            places.add(j);
        Collections.shuffle(places);
        for(int i =0 ;i < env.config.tableSize ;i++){
            if(table.slotToCard[places.get(i)] != null){
                deck.add(table.slotToCard[places.get(i)]);
                table.removeCard(places.get(i));
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int max = -1;
        int winnersAmount = 1;
        for(Player player:players){
            if(player.score()> max){
                max = player.score();
                winnersAmount = 1;
            }
            else if(player.score() == max){
                winnersAmount++;
            }         
        }
        int[] winners = new int[winnersAmount];
        int index = 0;
        for(Player player:players){
            if(player.score() == max){
                winners[index] = player.id;
                index++;
            }
        }
        env.ui.announceWinner(winners);
        // TODO implement
    }

    public void addToQueue(int playerID){
        dealerQueue.offer(playerID);
    }
}


