package players.groupx;
import utils.Types;
import utils.Vector2d;

import java.util.*;
import java.io.*;


/**
 * Contains all the relevant functions for the GroupXpommerdude
 */
public class GroupXutils {

    HashMap<Integer, ActionDistribution> actionDistributionsMCTS;
    HashMap<Integer, ActionDistribution> actionDistributionsRHEA;
    Random rand = new Random();

    // MB: Constructor: Read the trained tables
    public GroupXutils(){
        actionDistributionsRHEA = retrieveActionDistributions("hashMapRHEA.ser");
        actionDistributionsMCTS = retrieveActionDistributions("hashMapMCTS.ser");

        if(actionDistributionsMCTS == null){
            System.out.println("Error: Serialised MCTS trained table was not read");
        } else {
            System.out.println("MCTS Trained table found and is: ");
            printActionDistributions(actionDistributionsMCTS);
            System.out.println("-----------------------------------------------");
        }

        if(actionDistributionsRHEA == null){
            System.out.println("Error: Serialised REHA trained table was not read");
        } else {
            System.out.println("REHA Trained table found and is: ");
            printActionDistributions(actionDistributionsRHEA);
            System.out.println("-----------------------------------------------");
        }
    }

    Integer[] surroundingsMap = new Integer[]{
            1,2,3,4,5,1,6,6,6,7,7,7,7,7
    };

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
        return new Vector2d(0,0);
    }

    // If we know where the enemy was the last tick, we only need to check a few places (more efficient than whole board search).
    //TODO: Implement faster findEnemyPosition method when the previous position is known, handling also when we'd exceed the board array bounds.
    public Vector2d findEnemyPosition(Types.TILETYPE[][] board,Types.TILETYPE enemy, Vector2d enemyPrevPos){
        int width = board.length;
        int height = board[0].length;
        int y = enemyPrevPos.y;
        int x = enemyPrevPos.x;
        // Not moved
        if(board[y][x] == enemy) {
            return new Vector2d(x, y);
        }
        if(y != 0) {
            if (board[y - 1][x] == enemy) {return new Vector2d(x, y - 1); }
        }
        if(y < height-1){
            if(board[y+1][x] == enemy){ return new Vector2d(x, y+1); }
        }
        if(x != 0){
            if(board[y][x-1] == enemy){ return new Vector2d(x-1, y); }
        }
        if(x < width-1){
            if(board[y][x+1] == enemy){ return new Vector2d(x+1, y); }
        }
        return enemyPrevPos;
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
        ///MB: Return 0 if MCTS. Return 1 if REHA.
        //double mctsSimilarity = computeActionSimilarity(0, actionHistory);
        //double rheaSimilarity = computeActionSimilarity(1, actionHistory);
        double mctsSimilarity = computeActionPercentage(0, actionHistory);
        double rheaSimilarity = computeActionPercentage(1, actionHistory);

        //MB: Similarity debugging
        //System.out.println("MCTSSimilarity: "+mctsSimilarity);
        //System.out.println("REHASimilarity: "+rheaSimilarity);
        //System.out.println("------------------------------");
        //printActionDistributions(actionHistory);

        if(mctsSimilarity<rheaSimilarity){
            return 1;
        } else {
            return 0;
        }
    }

    // MB: We can choose whether to compute closeness of actions taken with Percentage or Similarity
    // MB: Or choose to compare them as part of one of the experiments?

    // MB: Percentage = Average % that the action taken in the history was taken in the strategy. Weighted by actions.
    // MB: Similarity = Cossine Similarity (angle between) ActionDistribution of the same surroundings.
    // Weighted by unique surroundings.

    public double computeActionPercentage(Integer strategy, HashMap<Integer,ActionDistribution> actionHistory) {
        HashMap<Integer, ActionDistribution> actionStrategy;
        if(strategy == 0){
            actionStrategy = actionDistributionsMCTS;
        } else {
            actionStrategy = actionDistributionsRHEA;
        }
        return computeActionSimilarity(actionStrategy, actionHistory);
    }

    public double computeActionPercentage(HashMap<Integer,ActionDistribution> actionStrategy, HashMap<Integer,ActionDistribution> actionHistory){
        Iterator hmIterator = actionHistory.entrySet().iterator();
        int N = 0;
        double percentage = 0;

        // MB: Need to iterate through the HashMap
        while (hmIterator.hasNext()) {
            Map.Entry element = (Map.Entry)hmIterator.next();
            //MB: Pull out the surroundings and ActionDistribution
            int surroundings = (int)element.getKey();
            ActionDistribution actionsTaken = (ActionDistribution)element.getValue();

            //MB: If surroundings don't exist in table, add a neutral similarity
            if(!actionStrategy.containsKey(surroundings)){
                percentage += 0.16;
            } else{
                //MB: How similar are the actions taken in this surroundings by the player compared to the strategy table?
                percentage += percentageOfActions(actionStrategy.get(surroundings), actionsTaken);
            }
            N++;
        }
        //MB: Returns average pecentage, a number between 0 and 1
        return percentage/N;
    }

    public double computeActionSimilarity(Integer strategy, HashMap<Integer,ActionDistribution> actionHistory){
        //MB: Given a strategy lookup index and surroundings:action distribution history, compute similarity
        //MB: strategy = index of learnt table to look up, 0 =MCTS, 1=REHA
        //todo: Only call this function once every few ticks. I think it will be expensive..
        HashMap<Integer, ActionDistribution> actionStrategy;
        if(strategy == 0){
            actionStrategy = actionDistributionsMCTS;
        } else {
            actionStrategy = actionDistributionsRHEA;
        }
        return computeActionSimilarity(actionStrategy, actionHistory);
    }

    public double computeActionSimilarity(HashMap<Integer,ActionDistribution> actionStrategy, HashMap<Integer,ActionDistribution> actionHistory){
        //MB: Uses cossine similarity
        //MB: Unique surroundings are treated differently and each unique surrounding has equal weigt
        //MB: actionStrategy = learnt table, actionHistory = observing what the enemy did
        Iterator hmIterator = actionHistory.entrySet().iterator();
        int N = 0;
        double similarity = 0;

        // MB: Need to iterate through the HashMap
        while (hmIterator.hasNext()) {
            Map.Entry element = (Map.Entry)hmIterator.next();
            //MB: Pull out the surroundings and ActionDistribution
            int surroundings = (int)element.getKey();
            ActionDistribution actionsTaken = (ActionDistribution)element.getValue();

            //MB: If surroundings don't exist in table, add a neutral similarity
            if(!actionStrategy.containsKey(surroundings)){
                similarity += 0.5;
            } else {
                similarity += similarityOfActions(actionStrategy.get(surroundings), actionsTaken);
            }
            N++;
        }
        //MB: Returns average similarity, a number between 0 and 1
        //MB: Note N here is number of unique surroundings in the enemies history
        return similarity/N;
    }

    public double similarityOfActions(ActionDistribution referenceActions, ActionDistribution takenActions){
        // a is an ActionDistribution to compare to this one

        // MB: compute cosine similarity defined as x.y / (||x|| ||y||). Bounded between 0 and 1.
        // It's a measure of closeness between 2 vectors
        double x = takenActions.magnitude();
        double y = referenceActions.magnitude();
        int dotproduct = 0;

        for(int i=0; i<= 5; i++) {
            dotproduct += takenActions.getActionCount(i) * referenceActions.getActionCount(i);
        }
        return dotproduct/(x*y);
    }

    private double percentageOfActions(ActionDistribution referenceActions, ActionDistribution takenActions){
        //MB: Runs through each action in takenActions. Compute average % times the action that was taken was taken in
        // the reference actions.
        int NtakenAction = 0;
        double sumPerc = 0;
        double percReference = 0;

        // Iterate through each actions
        for(int i=0; i<= 5; i++) {
            //MB: Compute percentage this was taken in reference
            percReference = (double)referenceActions.getActionCount(i)/(double)referenceActions.sum();

            //MB: This is the number of times action i was taken. So calculate % taken in reference and multiply by this
             NtakenAction = takenActions.getActionCount(i);
             sumPerc += NtakenAction * percReference;
        }
        //MB: Return average percentage times the action taken was taken by the reference
        return sumPerc/takenActions.sum();
    }

    public int returnRandomObservation(int surroundings){
        // Return a random, BUT VALID observation given a surroundings (for fair comparison in opponent modelling experiment).
        List<Integer> validObservations = new ArrayList<Integer>();
        validObservations.add(0);

        int surroundingsComponent = 0;
        for(int i=0;i<=3;i++) {
            // Only add if there is passage,bomb, power up or agent
            surroundingsComponent = surroundings % 10;
            if(surroundingsComponent == 1 || surroundingsComponent== 4 || surroundingsComponent== 5 || surroundingsComponent== 6 || surroundingsComponent== 7) {
                validObservations.add(i);
            }
            surroundings = surroundings/10;
        }
        return rand.nextInt(validObservations.size());
    }

    public void printPredictionAccuracy(String[][] accuracy){
        // Opponent
        for (int i = 0; i<= 2; i++) {
            for (int j = 0; j<= 800; j++) {
                if(accuracy[i][j] != null) {
                    System.out.println(accuracy[i][j]);
                }
            }
        }
    }

    public void savePredictionAccuracy(String[][] accuracy){
        try(FileWriter fw = new FileWriter("predictionAccuracy.txt", true);
            BufferedWriter bw = new BufferedWriter(fw);
            PrintWriter out = new PrintWriter(bw))
        {
            for (int i = 0; i<= 2; i++) {
                for (int j = 0; j<= 800; j++) {
                    if(accuracy[i][j] != null) {
                        out.println(accuracy[i][j]);
                    }
                }
            }
            out.close();
        } catch (IOException e) {
        }
    }

    public HashMap<Integer, ActionDistribution> retrieveActionDistributions(String hashMapFilename){

        // TODO: Make sure when we submit our code, the learnt tables can be read.
        //  Shouldn't assume we can make folders. It should be in the same folder that the class is in
        if(!new File("./hashmaps/"+hashMapFilename).exists()){
            System.out.println("Couldn't find trained table: "+hashMapFilename + " so return a blank one");
            return new HashMap<Integer, ActionDistribution>();
        }

        // Retrieve the Serialized distributions from last run
        HashMap<Integer, ActionDistribution> map = null;
        try
        {
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
        return map;
    }

    public void saveActionDistributions(HashMap<Integer, ActionDistribution> actionDistributions, String f){
        //If hashmap folder does not exist: make one.
        File hashfolder = new File("./hashmaps/");
        if (!hashfolder.exists()) {
            new File ("./hashmaps/").mkdir();
            System.out.println("Make hashmaps folder");
        }

        try {
            FileOutputStream fos = new FileOutputStream("./hashmaps/"+f);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(actionDistributions);
            oos.close();
            fos.close();
            System.out.printf("Training table "+f+ " was updated.");
        }catch(IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
