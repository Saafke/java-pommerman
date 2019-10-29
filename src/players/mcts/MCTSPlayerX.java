package players.mcts;

import core.GameState;
import players.optimisers.ParameterizedPlayer;
import players.Player;
import utils.ElapsedCpuTimer;
import utils.Types;

import java.util.ArrayList;
import java.util.Random;

public class MCTSPlayerX extends ParameterizedPlayer {

    int nEnemies;
    /**
     * Random generator.
     */
    private Random m_rnd;

    /**
     * All actions available.
     */
    public Types.ACTIONS[] actions;

    //**
    public SingleTreeNode lastTree = null;
    /**
     * Params for this MCTS
     */
    public MCTSParams params;

    public MCTSPlayerX(long seed, int id) {
        this(seed, id, new MCTSParams());
    }

    public MCTSPlayerX(long seed, int id, MCTSParams params) {
        super(seed, id, params);
        reset(seed, id);

        ArrayList<Types.ACTIONS> actionsList = Types.ACTIONS.all();
        actions = new Types.ACTIONS[actionsList.size()];
        int i = 0;
        for (Types.ACTIONS act : actionsList) {
            actions[i++] = act;
        }
    }

    @Override
    public void reset(long seed, int playerID) {
        this.seed = seed;
        this.playerID = playerID;
        m_rnd = new Random(seed);

        this.params = (MCTSParams) getParameters();
        if (this.params == null) {
            this.params = new MCTSParams();
            super.setParameters(this.params);
        }
    }

    @Override
    public Types.ACTIONS act(GameState gs) {

        // TODO update gs
        if (gs.getGameMode().equals(Types.GAME_MODE.TEAM_RADIO)){
            int[] msg = gs.getMessage();
        }

        ElapsedCpuTimer ect = new ElapsedCpuTimer();
        ect.setMaxTimeMillis(params.num_time);

        // Number of actions available
        int num_actions = actions.length;

        SingleTreeNode m_root;
        int nEnemiesNow = gs.getAliveEnemyIDs().size();


        //Reuse the last gametree -------
        if(lastTree != null) {
            //System.out.println("Using last tree");
            m_root = lastTree;
            //m_root.discount();
            m_root.setRootGameState(gs);
        }
        //--------------------------------
        else{
            //Create a new tree with new root
            m_root = new SingleTreeNode(params, m_rnd, num_actions, actions);
            // set the current game as the root
            m_root.setRootGameState(gs);
        }

        if(nEnemiesNow != nEnemies){ //enemydied - discard tree
            //Create a new tree with new root
            m_root = new SingleTreeNode(params, m_rnd, num_actions, actions);
            // set the current game as the root
            m_root.setRootGameState(gs);
        }

        //update enemy count
        nEnemies = nEnemiesNow;

        //Determine the action using MCTS...
        m_root.mctsSearch(ect);

        //Determine the best action to take and return it.
        int action = m_root.mostVisitedAction();

        //**In the game-tree take the action and save it
        //lastTree = m_root.returnChild(action);

        // TODO update message memory

        //Print elapsed time to find this action
        //System.out.println(ect);

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
        return new MCTSPlayer(seed, playerID, params);
    }
}