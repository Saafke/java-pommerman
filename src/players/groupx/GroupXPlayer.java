package players.groupx;

import core.GameState;
import players.Player;
import players.optimisers.ParameterizedPlayer;
import utils.ElapsedCpuTimer;
import utils.Types;
import utils.Vector2d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

//todo: Worried about time with all this crap that has been added...
// do a test of how long the opponent evaluation/ strategy switching part is taking.
public class GroupXPlayer extends ParameterizedPlayer {
    private Random m_rnd;
    public Types.ACTIONS[] actions;
    public GroupXParams params;
    private HashMap<Integer, ActionDistribution> trainingActions;

    //MB: IF YOU WANT TO TRAIN/NOT TRAIN, CHANGE THESE!!!!!!!!!!!!!!!
    //MB: GroupXPlayer needs to be one of the agents (any position). All the rest need to be the Agent being trained.
    private boolean TRAINING = false;
    private String HASHMAPPATH = "hashMapMCTS.ser";

    //XW: relevant functions/utils for this player
    public GroupXutils utilsX;

    //MB: List to retrieve list of alive enemies each time (these will be the ones iterated over).
    private ArrayList<Types.TILETYPE> aliveEnemies;

    //MB: Store enemy positions like AGENT0: (120,60) AGENT1: (50, 60)
    private HashMap<Types.TILETYPE, Vector2d> enemyPositions;

    //MB: Store enemy action predictions to compare to next time (Opponent Modelling).
    private HashMap<Types.TILETYPE, Integer> enemyPredictedActions;
    private String[][] predictionAccuracy = new String[3][900];

    private int tick = 0;

    //MB: Store enemy actions for each enemy
    private HashMap<Types.TILETYPE, HashMap<Integer, ActionDistribution>> enemyActions;

    //MB: Store the assumed enemy strategy. 0 = MCTS, 1 = REHA. Needs to be public so GroupX node can see it.
    private HashMap<Types.TILETYPE,Integer> enemyStrategies;

    Types.TILETYPE[][] oldBoard;

    private HashMap<Integer, ActionDistribution> MCTS_TABLE;
    private HashMap<Integer, ActionDistribution> RHEA_TABLE;

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
        System.out.println("Group X for the win");
        reset(seed, id);
        this.utilsX = utils;
        tick = 0;
        ArrayList<Types.ACTIONS> actionsList = Types.ACTIONS.all();
        actions = new Types.ACTIONS[actionsList.size()];
        int i = 0;
        for (Types.ACTIONS act : actionsList) {
            actions[i++] = act;
        }
        aliveEnemies = new ArrayList<Types.TILETYPE>();
        enemyPositions = new HashMap<Types.TILETYPE, Vector2d>();
        enemyActions = new HashMap<Types.TILETYPE, HashMap<Integer, ActionDistribution>>();
        enemyPredictedActions = new HashMap<Types.TILETYPE, Integer>();
        // Start off assuming MCTS. It's ok that this includes us, we'll never look us up.
        enemyStrategies = new HashMap<Types.TILETYPE, Integer>();
        enemyStrategies.put(Types.TILETYPE.AGENT0, 0);
        enemyStrategies.put(Types.TILETYPE.AGENT1, 0);
        enemyStrategies.put(Types.TILETYPE.AGENT2, 0);
        enemyStrategies.put(Types.TILETYPE.AGENT3, 0);

        //MB: If training,retrieve hash map
        if(TRAINING) {
            System.out.println("GroupXPlayer: Training mode, Will attempt to retrieve "+HASHMAPPATH + " to add to it.");
            trainingActions = utilsX.retrieveActionDistributions(HASHMAPPATH);
        }
    }

    @Override
    public void reset(long seed, int playerID) {
        this.seed = seed;
        this.playerID = playerID;
        m_rnd = new Random(seed);
        tick = 0;

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

        //MB: Only retrieve and iterate over enemies that are alive.
        aliveEnemies = gs.getAliveEnemyIDs();

        Types.TILETYPE[][] newBoard = gs.getBoard();
        if(oldBoard == null) { oldBoard = gs.getBoard(); }

        // Go through each enemy, updating positions and actions
        for (Types.TILETYPE enemy : aliveEnemies) {
            // MB: Update position: Using the previous position known method
            Vector2d newPosition;
            if (!enemyPositions.containsKey(enemy)) {
                //MB: Slow find
                enemyPositions.put(enemy, utilsX.findEnemyPosition(newBoard, enemy));
            }
            Vector2d oldPosition = enemyPositions.get(enemy);
            newPosition = utilsX.findEnemyPosition(newBoard,enemy,oldPosition);

            // MB: Infer and update actions.
            Types.ACTIONS enemyAction = utilsX.inferEnemyAction(oldPosition, newPosition, newBoard,
                    gs.getBombBlastStrength(), gs.getBombLife());

            int enemySurroundings = utilsX.getSurroundingsIndex(oldPosition, oldBoard);

            if (!enemyActions.containsKey(enemy)) {
                enemyActions.put(enemy, new HashMap<Integer, ActionDistribution>());
            }
            if(!enemyActions.get(enemy).containsKey(enemySurroundings)) {
                enemyActions.get(enemy).put(enemySurroundings,new ActionDistribution());
            }
            //MB: Update the Action Distribution with enemies old surroundings and the inferred action
            enemyActions.get(enemy).get(enemySurroundings).updateActionCount(enemyAction);
            //MB: Update enemy position with the new position
            enemyPositions.put(enemy, newPosition);

            //MB: RECORD TRAINING
            if(TRAINING){
                if(!trainingActions.containsKey(enemySurroundings)){
                    trainingActions.put(enemySurroundings, new ActionDistribution());
                }
                trainingActions.get(enemySurroundings).updateActionCount(enemyAction);
            }

            //MB: STRATEGY SWITCHING
            int bestFitStrategy = utilsX.strategySwitch(enemyActions.get(enemy));
            enemyStrategies.put(enemy,bestFitStrategy);

            //MB: OPPONENT MODELLING EXPERIMENT

            // Store prediction success 1 or 0
            int observedAction = enemyAction.getKey();
            int randomObservation = utilsX.returnRandomObservation(enemySurroundings);
            if (!enemyPredictedActions.containsKey(enemy)) { enemyPredictedActions.put(enemy, 0); }

            // Opponent, Tick, surroundings, Assumed Strategy, Predicted Action, RandomObservation, Observed Action, Success, ReferenceSuccess
            predictionAccuracy[enemy.getKey()-11][tick] = (enemy.getKey()-11)+","+tick+","+enemySurroundings+","+enemyStrategies.get(enemy)+","+enemyPredictedActions.get(enemy)+","+randomObservation+","+observedAction+","
                    + (observedAction == enemyPredictedActions.get(enemy) ? 1:0) + "," + (observedAction == randomObservation ? 1:0);

            // Debugging: Surroundings
            if(enemy == Types.TILETYPE.AGENT2) {
                //System.out.println("Old position: "+oldPosition.toString());
                //System.out.println("New position: "+newPosition.toString());
                //System.out.println(enemySurroundings);
            }

            /*Debugging:
            if(enemy == Types.TILETYPE.AGENT1) {
                System.out.println("Predicted: " + enemyPredictedActions.get(enemy));
                System.out.println("Actual: " + observedAction);
                if (enemyStrategies.get(enemy) == 0) {
                    if (utilsX.actionDistributionsMCTS.containsKey(enemySurroundings)) {
                        System.out.println(enemySurroundings + utilsX.actionDistributionsMCTS.get(enemySurroundings).toString());
                    }
                } else {
                    if (utilsX.actionDistributionsRHEA.containsKey(enemySurroundings)) {
                        System.out.println(enemySurroundings + utilsX.actionDistributionsRHEA.get(enemySurroundings).toString());
                    }
                }
            }
            */

            // Predict opponents next action
            int predictedAction = 0;
            double randomnessComponent = 1;
            if(enemyStrategies.get(enemy) == 0){
                if(utilsX.actionDistributionsMCTS.containsKey(enemySurroundings)){
                    predictedAction = utilsX.actionDistributionsMCTS.get(enemySurroundings).sampleAction();
                    randomnessComponent = Math.max(1/(double)utilsX.actionDistributionsMCTS.get(enemySurroundings).sum(),0);
                }
            } else {
                if(utilsX.actionDistributionsRHEA.containsKey(enemySurroundings)){
                    predictedAction = utilsX.actionDistributionsRHEA.get(enemySurroundings).sampleAction();
                    randomnessComponent = Math.max(1/(double)utilsX.actionDistributionsRHEA.get(enemySurroundings).sum(),0);
                }
            }

            //MB: Add randomness to offset some SurroundingsIndex having low volume: more training was needed
            if(randomnessComponent > m_rnd.nextDouble()){
                predictedAction = m_rnd.nextInt(gs.nActions());
            }
            enemyPredictedActions.put(enemy,predictedAction);
        }

        //MB: We are done with the old board, update it
        oldBoard = newBoard;

        // Run MCTS
        // Number of actions available
        int num_actions = actions.length;

        // Root of the tr
        GroupXSingleTreeNode m_root = new GroupXSingleTreeNode(params, utilsX, m_rnd, num_actions, actions);
        m_root.setRootGameState(gs);

        //Determine the action using MCTS...
        //MB: We need to provide the enemy strategies to this function otherwise nodes can't see them.
        m_root.mctsSearch(ect,enemyStrategies);

        //Determine the best action to take and return it.
        int action = m_root.mostVisitedAction();
        //... and return it.

        tick++;
        return actions[action];
    }

    @Override
    public void result(double reward) {
        //MB: This should be called when everyone dies.
        if(TRAINING) {
            utilsX.saveActionDistributions(trainingActions, HASHMAPPATH);
        }

        utilsX.printPredictionAccuracy(predictionAccuracy);
        utilsX.savePredictionAccuracy(predictionAccuracy);

        super.result(reward);
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