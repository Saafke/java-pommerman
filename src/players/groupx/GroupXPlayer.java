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

//todo: Worried about time with all this crap that has been added...
// do a test of how long the opponent evaluation/ strategy switching part is taking.
public class GroupXPlayer extends ParameterizedPlayer {
    private Random m_rnd;
    public Types.ACTIONS[] actions;
    public GroupXParams params;

    //XW: relevant functions/utils for this player
    public GroupXutils utilsX;

    //MB: List to retrieve list of alive enemies each time (these will be the ones iterated over).
    private ArrayList<Types.TILETYPE> aliveEnemies;

    //MB: Store enemy positions like AGENT0: (120,60) AGENT1: (50, 60)
    private HashMap<Types.TILETYPE, Vector2d> enemyPositions;

    //MB: Store enemy actions. Yes, we're storing a HashMap of Hashmaps, because we PROFESSIONALS
    private HashMap<Types.TILETYPE, HashMap<Integer, ActionDistribution>> enemyActions;

    //MB: Store the assumed enemy strategy. 0 = MCTS, 1 = REHA. Needs to be public so GroupX node can see it.
    private HashMap<Types.TILETYPE,Integer> enemyStrategies;

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
        this(seed, id, new GroupXParams(), new GroupXutils());
    }

    public GroupXPlayer(long seed, int id, GroupXParams params, GroupXutils utils) {
        super(seed, id, params);
        reset(seed, id);
        this.utilsX = utils;
        ArrayList<Types.ACTIONS> actionsList = Types.ACTIONS.all();
        actions = new Types.ACTIONS[actionsList.size()];
        int i = 0;
        for (Types.ACTIONS act : actionsList) {
            actions[i++] = act;
        }

        aliveEnemies = new ArrayList<Types.TILETYPE>();
        enemyPositions = new HashMap<Types.TILETYPE, Vector2d>();
        enemyActions = new HashMap<Types.TILETYPE, HashMap<Integer, ActionDistribution>>();
        // Start off assuming MCTS. It's ok that this includes us, we'll never look us up.
        enemyStrategies = new HashMap<Types.TILETYPE, Integer>();
        enemyStrategies.put(Types.TILETYPE.AGENT0, 0);
        enemyStrategies.put(Types.TILETYPE.AGENT1, 0);
        enemyStrategies.put(Types.TILETYPE.AGENT2, 0);
        enemyStrategies.put(Types.TILETYPE.AGENT3, 0);

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
        GroupXSingleTreeNode m_root = new GroupXSingleTreeNode(params, utilsX, m_rnd, num_actions, actions);
        m_root.setRootGameState(gs);

        //Determine the action using MCTS...
        //MB: We need to provide the enemy strategies to this function otherwise nodes can't see them.
        m_root.mctsSearch(ect,enemyStrategies);

        //MB: Only retrieve and iterate over enemies that are alive.
        aliveEnemies = gs.getAliveEnemyIDs();

        Types.TILETYPE[][] newBoard = gs.getBoard();
        if(oldBoard == null) { oldBoard = gs.getBoard(); }

        // Go through each enemy, updating positions and actions
        for (Types.TILETYPE enemy : aliveEnemies) {
            // MB: Update position
            Vector2d newPosition = utilsX.findEnemyPosition(newBoard, enemy);
            if (!enemyPositions.containsKey(enemy)) {
                enemyPositions.put(enemy, newPosition);
            }
            Vector2d oldPosition = enemyPositions.get(enemy);

            // MB: Infer and update actions.
            Types.ACTIONS action = utilsX.inferEnemyAction(oldPosition, newPosition, newBoard,
                    gs.getBombBlastStrength(), gs.getBombLife());
            int enemySurroundings = utilsX.getSurroundingsIndex(oldPosition, oldBoard);

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
//            if(enemy == Types.TILETYPE.AGENT1) {
//                System.out.println(enemy + " action from GroupXPlayer perspective was: " + action + " with surroundings: " +enemySurroundings);
//                printActionDistributions(enemyActions.get(enemy));
//                System.out.println("\n");
//            }

            //TODO: Seperate this out into a function and
            //MB: Strategy switching assessment: Get integer of what strategy best fits this enemies actions
            //MB: 0 MCTS, 1 REHA
            int bestFitStrategy = utilsX.strategySwitch(enemyActions.get(enemy));
            // MB: Update enemy strategy with the best fit one
            enemyStrategies.put(enemy,bestFitStrategy);

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
        return new GroupXPlayer(seed, playerID, params, utilsX);
    }

}
