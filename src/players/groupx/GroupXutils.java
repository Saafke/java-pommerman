package players.groupx;

import utils.ActionDistribution;
import utils.Types;
import utils.Vector2d;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Contains all the relevant functions for the GroupXpommerdude
 */
public class GroupXutils {

    // XW: Empty constructor
    public GroupXutils(){
        //
    }

    Integer[] surroundingsMap = new Integer[]{
            1,2,3,4,5,1,6,6,6,7,7,7,7,7
    };

    HashMap<Integer, ActionDistribution> actionDistributionsMCTS = retrieveActionDistributions("hashMapMCTS.ser");
    HashMap<Integer, ActionDistribution> actionDistributionsREHA = retrieveActionDistributions("hashMapREH.ser");

    // For when we don't know where the enemy is (eg. at the start). Search the whole board.
    public Vector2d findEnemyPosition(Types.TILETYPE[][] board, Types.TILETYPE enemy){
        int width = board.length;
        int height = board[0].length;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (board[y][x] == enemy) {
                    return new Vector2d(x, y);
                }
            }
        }
        //TODO: Why is it that sometimes enemies can't be found?
        System.out.println("Error: Couldn't find enemy position "+enemy);
        return new Vector2d(0,0);
    }

    // If we know where the enemy was the last tick, we only need to check a few places (more efficient than whole board search).
    //TODO: Implement faster findEnemyPosition method when the previous position is known, handling also when we'd exceed the board array bounds.
    public Vector2d findEnemyPosition(Types.TILETYPE[][] board,Types.TILETYPE enemy, Vector2d enemyPrevPos){
        if(board[enemyPrevPos.y][enemyPrevPos.x] == enemy){
            return new Vector2d(enemyPrevPos.x, enemyPrevPos.y);
        }
        if(board[enemyPrevPos.y+1][enemyPrevPos.x] == enemy){
            return new Vector2d(enemyPrevPos.x, enemyPrevPos.y+1);
        }
        return null;
    }

    // Handle when surroundings include edge of map (treat as Rigid)
    public int validateSurroundings(int y, int x, Types.TILETYPE[][] board){
        if (y<0 || x<0 || y>=board.length || x>= board.length){
            return 2;
        } else {
            // Return the surroundings equivalent of this Tile
            return surroundingsMap[board[y][x].getKey()];
        }
    }

    public int getSurroundingsIndex(Vector2d playerPos, Types.TILETYPE[][] board) {
        // Get Tile values of surrounding Tiles (clockwise: Top, Right, Down, Left).
        // Top left is origin, 0,0
        // Convert TileMap Integers to Surroundings Integers
        Integer[] surroundings = new Integer[4];
        surroundings[0] = validateSurroundings(playerPos.y-1, playerPos.x, board);
        surroundings[1] = validateSurroundings(playerPos.y, playerPos.x+1, board);
        surroundings[2] = validateSurroundings(playerPos.y+1, playerPos.x, board);
        surroundings[3] = validateSurroundings(playerPos.y, playerPos.x-1, board);
        return Integer.valueOf(String.valueOf(surroundings[0]) + String.valueOf(surroundings[1]) + String.valueOf(surroundings[2])+ String.valueOf(surroundings[3]));
    }

    public void printActionDistributions(HashMap<Integer, ActionDistribution> countMap) {
        for (HashMap.Entry mapElement : countMap.entrySet()) {
            System.out.println(mapElement.toString());
        }
    }

    //TODO: Find a way to handle the fact that an enemy can choose an action like LEFT when there is a wall left.
    // todo They won't move but they haven't chosen action STOP..(does this matter as the actual action is STOP anyway?)
    // XW: answer - probably can't know since we can only "observe" other players, so i think left=stop in this case.
    public Types.ACTIONS inferEnemyAction(Vector2d oldPos, Vector2d newPos, Types.TILETYPE[][] board,
                                           int[][] bombBlastStrength, int[][] bombLife){

        //XW: enemy did not move positions
        if (newPos.equals(oldPos)){
            //XW: check if agent placed bomb or not -- bombstrength > 0 and bomblife == 9 (meaning it was just dropped)
            if (bombBlastStrength[oldPos.y][oldPos.x] > 0 && bombLife[oldPos.y][oldPos.x] == 9) {
                return Types.ACTIONS.ACTION_BOMB;
            }
            else{ //XW: the enemy did nothing (there might be a bomb still but it was dropped earlier).
                return Types.ACTIONS.ACTION_STOP;
            }
        } else if (newPos.equals(oldPos.add(new Vector2d(0,-1)))){
            return Types.ACTIONS.ACTION_UP;
        } else if (newPos.equals(oldPos.add(new Vector2d(0,1)))){
            return Types.ACTIONS.ACTION_DOWN;
        } else if (newPos.equals(oldPos.add(new Vector2d(-1,0)))){
            return Types.ACTIONS.ACTION_LEFT;
        } else if (newPos.equals(oldPos.add(new Vector2d(1,0)))){
            return Types.ACTIONS.ACTION_RIGHT;
        } else {
            System.out.println("Infer enemy action failed");
            return Types.ACTIONS.ACTION_STOP;
        }
    }

    public int strategySwitch(HashMap<Integer,ActionDistribution> actionHistory){
        //MB: Determine what the strategy should be, given a list of enemy actions
        ///MB: Return 0 if MCTS. Return 1 if REHA
        double mctsSimilarity = computeActionSimilarity(0, actionHistory);
        double rehaSimilarity = computeActionSimilarity(1, actionHistory);

        if(mctsSimilarity<rehaSimilarity){
            return 1;
        } else {
            return 0;
        }
    }

    public double computeActionSimilarity(Integer strategy, HashMap<Integer,ActionDistribution> actionHistory){
        //MB: Given a strategy lookup index and surroundings:action distribution history, compute accuracy
        //MB: actions is a surroundings ActionDistribution tuple
        //todo: Only call this function once every few ticks. I think it will be expensive..
        HashMap<Integer, ActionDistribution> actionStrategy;
        if(strategy == 0){
            actionStrategy = actionDistributionsMCTS;
        } else {
            actionStrategy = actionDistributionsREHA;
        }

        //MB: Go through each action, adding up the
        //MB: actionHistory is all the surroundings:ActionDistribution
        //MB: actionsTaken is the ActionDistribution for a specific surroundings
        Iterator hmIterator = actionHistory.entrySet().iterator();
        int N = 0;
        double similarity = 0;

        // MB: Need to iterate through the HashMap
        while (hmIterator.hasNext()) {
            Map.Entry element = (Map.Entry)hmIterator.next();
            //MB: Pull out the surroundings and ActionDistribution
            int surroundings = (int)element.getKey();
            ActionDistribution actionsTaken = (ActionDistribution)element.getValue();
            if(!actionStrategy.containsKey(surroundings)){
                return 0;
            }
            //MB: How similar are the actions taken in this surroundings by the player compared to the strategy table?
            similarity += percentageAction(actionStrategy.get(surroundings), actionsTaken);
            N++;
        }
        return similarity/N;
    }

    public double computeActionSimilarity(HashMap<Integer,ActionDistribution> actionStrategy, HashMap<Integer,ActionDistribution> actionHistory){
        //MB: Given a strategy lookup index and surroundings:action distribution history, compute accuracy
        //MB: actions is a surroundings ActionDistribution tuple
        //todo: Only call this function once every few ticks. I think it will be expensive..

        //MB: Go through each action, adding up the
        //MB: actionHistory is all the surroundings:ActionDistribution
        //MB: actionsTaken is the ActionDistribution for a specific surroundings
        Iterator hmIterator = actionHistory.entrySet().iterator();
        int N = 0;
        double similarity = 0;

        // MB: Need to iterate through the HashMap
        while (hmIterator.hasNext()) {
            Map.Entry element = (Map.Entry)hmIterator.next();
            //MB: Pull out the surroundings and ActionDistribution
            int surroundings = (int)element.getKey();
            ActionDistribution actionsTaken = (ActionDistribution)element.getValue();
            if(!actionStrategy.containsKey(surroundings)){
                return 0;
            }
            //MB: How similar are the actions taken in this surroundings by the player compared to the strategy table?
            similarity += percentageAction(actionStrategy.get(surroundings), actionsTaken);
            N++;
        }
        return similarity/N;
    }

    private HashMap<Integer, ActionDistribution> retrieveActionDistributions(String hashMapFilename){

        // TODO: Make sure when we submit our code, the learnt tables can be read.
        //  Shouldn't assume we can make folders. It should be in the same folder that the class is in
        if(!new File("./hashmaps/"+hashMapFilename).exists()){
            return new HashMap<Integer, ActionDistribution>();
        }

        // Retrieve the Serialized distributions from last run
        HashMap<Integer, ActionDistribution> map = null;
        try
        {
            System.out.println(hashMapFilename);
            FileInputStream fis = new FileInputStream("./hashmaps/"+hashMapFilename);
            ObjectInputStream ois = new ObjectInputStream(fis);
            map = (HashMap) ois.readObject();
            ois.close();
            fis.close();
        }catch(IOException ioe)
        {
            ioe.printStackTrace();
            return null;
        }catch(ClassNotFoundException c)
        {
            System.out.println("Class not found");
            c.printStackTrace();
            return null;
        }
        System.out.println("Deserialized HashMap");
        // Display content using Iterator
//        Set set = map.entrySet();
//        Iterator iterator = set.iterator();
//        while(iterator.hasNext()) {
//            Map.Entry mentry = (Map.Entry)iterator.next();
//            System.out.print("key: "+ mentry.getKey() + " & Value: ");
//            System.out.println(mentry.getValue());
//        }

        return map;
    }

    // MB: Compares two Action Distributions for their similarity
    public double percentageAction(ActionDistribution referenceActions, ActionDistribution takenActions){
        // a is an ActionDistribution to compare to this one

        // MB: compute cosine similarity defined as x.y / (||x|| ||y||). Bounded between 0 and 1.
        // It's a measure of closeness between 2 vectors
        double x = takenActions.magnitude();
        double y = referenceActions.magnitude();
        int dotproduct = 0;
        System.out.println("Strategy magnitude was:"+y);
        System.out.println("History magnitude was:"+x);

        for(int i=0; i<= 5; i++) {
            dotproduct += takenActions.getActionCount(i) * referenceActions.getActionCount(i);
            System.out.println("Dotproduct at iteration:"+i+" was "+ dotproduct);
        }
        return dotproduct/(x*y);
    }
}
