package players.groupx;

import core.GameState;
import players.Player;
import players.mcts.MCTSPlayer;
import players.mcts.SingleTreeNode;
import players.optimisers.ParameterizedPlayer;
import utils.ActionDistribution;
import utils.ElapsedCpuTimer;
import utils.Types;
import utils.Vector2d;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.io.*;

import java.util.*;


public class GroupXPlayer extends ParameterizedPlayer {
    private Random m_rnd;
    public Types.ACTIONS[] actions;
    public GroupXParams params;
    //MB: List to retrieve list of alive enemies each time (these will be the ones iterated over).
    private ArrayList<Types.TILETYPE> aliveEnemies;
    //MB: Store enemy positions like AGENT0: (120,60) AGENT1: (50, 60)
    //MB: Indexed by Enemy.
    private HashMap<Types.TILETYPE, Vector2d> enemyPositions;
    //MB: Store enemy actions. Yes, we're storing a HashMap of Hashmaps, because we PROFESSIONALS
    //MB: Indexed by Enemy.
    private HashMap<Types.TILETYPE, HashMap<Integer, ActionDistribution>> enemyActions;
    Types.TILETYPE[][] oldBoard;

    private HashMap<Integer, ActionDistribution> MCTS_TABLE;
    private HashMap<Integer, ActionDistribution> REHA_TABLE;

    /**
     begin{itemize}
     \item 0: passage, 1
     \item 1: rigid, 2
     \item 2: wood, 3
     \item 3: bomb, 4
     \item 4: flames, 5
     \item 5: fog, treat as passage 1
     \item 6-8: powerups, treat the same, 6
     \item 9: dummyAgent, 7
     \item 10-13: Agents, treat the same, 7
     */
    Integer[] surroundingsMap = new Integer[]{
            1,2,3,4,5,1,6,6,6,7,7,7,7,7
    };

    public GroupXPlayer(long seed, int id) {
        this(seed, id, new GroupXParams());
    }

    public GroupXPlayer(long seed, int id, GroupXParams params) {
        super(seed, id, params);
        reset(seed, id);

        ArrayList<Types.ACTIONS> actionsList = Types.ACTIONS.all();
        actions = new Types.ACTIONS[actionsList.size()];
        int i = 0;
        for (Types.ACTIONS act : actionsList) {
            actions[i++] = act;
        }

        aliveEnemies = new ArrayList<Types.TILETYPE>();
        enemyPositions = new HashMap<Types.TILETYPE, Vector2d>();
        // Indexed by AGENT1, AGENT0. Stores Surroundings: Action distributions for
        enemyActions = new HashMap<Types.TILETYPE, HashMap<Integer, ActionDistribution>>();
    }

    @Override
    public void reset(long seed, int playerID) {
        this.seed = seed;
        this.playerID = playerID;
        m_rnd = new Random(seed);

        this.params = (GroupXParams) getParameters();
        if (this.params == null) {
            this.params = new GroupXParams();
            super.setParameters(this.params);
        }
    }

    @Override
    public Types.ACTIONS act(GameState gs) {

        ElapsedCpuTimer ect = new ElapsedCpuTimer();
        ect.setMaxTimeMillis(params.num_time);

        // Number of actions available
        int num_actions = actions.length;

        // Root of the tree
        GroupXSingleTreeNode m_root = new GroupXSingleTreeNode(params, m_rnd, num_actions, actions);
        m_root.setRootGameState(gs);

        //Determine the action using MCTS...
        m_root.mctsSearch(ect);

        //MB: Handle the assessment of Opponent Actions: Is the table performing well or should we switch?

        //MB: Need to store a list of assumed enemy outcomes and check against it.

        //MB: Only retrieve and iterate over enemies that are alive.
        aliveEnemies = gs.getAliveEnemyIDs();

        Types.TILETYPE[][] newBoard = gs.getBoard();
        //TODO: Handle start of the game better. At the moment it populates a STOP at the start.
        if(oldBoard == null) { oldBoard = gs.getBoard(); }

        // Go through each enemy, updating positions and actions
        for (Types.TILETYPE enemy : aliveEnemies) {
            // MB: Update position
            Vector2d newPosition = findEnemyPosition(newBoard, enemy);
            if (!enemyPositions.containsKey(enemy)) {
                //TODO: Handle start of the game better. At the moment it populates a STOP at the start.
                enemyPositions.put(enemy, newPosition);
            }
            Vector2d oldPosition = enemyPositions.get(enemy);

            // MB: Infer and update actions.
            Types.ACTIONS action = inferEnemyAction(oldPosition, newPosition, newBoard);
            int enemySurroundings = getSurroundingsIndex(oldPosition, oldBoard);

            if (!enemyActions.containsKey(enemy)) {
                enemyActions.put(enemy, new HashMap<Integer, ActionDistribution>());
            }

            if(!enemyActions.get(enemy).containsKey(enemySurroundings)) {
                enemyActions.get(enemy).put(enemySurroundings,new ActionDistribution());
            }
            //MB: Update the Action Distribution with enemies old surroundings and the inferred action
            enemyActions.get(enemy).get(enemySurroundings).updateActionCount(action);
            //MB: Update enemy position with the new position
            enemyPositions.put(enemy, newPosition);
            //MB: We are done with the old board, update it
            oldBoard = newBoard;
            //MB: Debugging
            if(enemy == Types.TILETYPE.AGENT1) {
                //System.out.println(enemy + " action from GroupXPlayer perspective was: " + action + " with surroundings: " +enemySurroundings);
                //printActionDistributions(enemyActions.get(enemy));
            }
        }

        //Determine the best action to take and return it.
        int action = m_root.mostVisitedAction();
        //... and return it.
        return actions[action];
    }

    @Override
    public int[] getMessage() {
        // default message
        int[] message = new int[Types.MESSAGE_LENGTH];
        message[0] = 1;
        return message;
    }

    @Override
    public Player copy() {
        return new GroupXPlayer(seed, playerID, params);
    }

    // For when we don't know where the enemy is (eg. at the start). Search the whole board.
    private Vector2d findEnemyPosition(Types.TILETYPE[][] board, Types.TILETYPE enemy){
        int width = board.length;
        int height = board[0].length;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (board[y][x] == enemy) {
                    return new Vector2d(x, y);
                }
            }
        }
        System.out.println("Error: Couldn't find enemy position "+enemy);
        return new Vector2d(0,0);
    }

    // If we know where the enemy was the last tick, we only need to check a few places (more efficient than whole board search).
    //TODO: Implement faster findEnemyPosition method when the previous position is known, handling also when we'd exceed the board array bounds.
    private Vector2d findEnemyPosition(Types.TILETYPE[][] board,Types.TILETYPE enemy, Vector2d enemyPrevPos){
        if(board[enemyPrevPos.y][enemyPrevPos.x] == enemy){
            return new Vector2d(enemyPrevPos.x, enemyPrevPos.y);
        }
        if(board[enemyPrevPos.y+1][enemyPrevPos.x] == enemy){
            return new Vector2d(enemyPrevPos.x, enemyPrevPos.y+1);
        }
        return null;
    }

    //TODO: Find a way to know that a bomb has been placed ; when is the board updated with the bomb? If it's only once a player has moved then that fucking sucks...
    //TODO: Find a way to handle the fact that an enemy can choose an action like LEFT when there is a wall left. They won't move but they haven't chosen action STOP..(does this matter as the actual action is STOP anyway?)
    private Types.ACTIONS inferEnemyAction(Vector2d oldPos, Vector2d newPos, Types.TILETYPE[][] board){
        // THIS DOES NOT RECORD BOMB ACTIONS PROPERLY
        if(board[oldPos.y][oldPos.x] == Types.TILETYPE.BOMB) {
            return Types.ACTIONS.ACTION_BOMB;
        }

        if (newPos.equals(oldPos)){
            return Types.ACTIONS.ACTION_STOP;
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

    private void computeActionSimilarity(String lookup,HashMap<Integer, ActionDistribution> actions){
        // Input String of table to lookup against, HashMap of viewed surroundings:actions pairs
        if(lookup == "MCTS"){
            return;
        } else {
            return;
        }
    }

    private int getSurroundingsIndex(Vector2d playerPos, Types.TILETYPE[][] board) {
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

    // Handle when surroundings include edge of map (treat as Rigid)
    private int validateSurroundings(int y,int x,Types.TILETYPE[][] board){
        if (y<0 || x<0 || y>=board.length || x>= board.length){
            return 2;
        } else {
            // Return the surroundings equivalent of this Tile
            return surroundingsMap[board[y][x].getKey()];
        }
    }

    private void printActionDistributions(HashMap<Integer, ActionDistribution> countMap) {
        for (HashMap.Entry mapElement : countMap.entrySet()) {
            System.out.println(mapElement.toString());
        }
    }

    private HashMap<Integer, ActionDistribution> retrieveTrainedDistributions(String hashMapFilename){
        // if hashmap of this filename not exists yet, make a empty one.
        if(!new File("./hashmaps/"+hashMapFilename).exists()){
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
        System.out.println("Deserialized HashMap");
        // Display content using Iterator
        Set set = map.entrySet();
        Iterator iterator = set.iterator();
        while(iterator.hasNext()) {
            Map.Entry mentry = (Map.Entry)iterator.next();
            System.out.print("key: "+ mentry.getKey() + " & Value: ");
            System.out.println(mentry.getValue());
        }

        return map;
    }
}
