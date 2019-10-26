package players.groupx;

import core.GameState;
import players.Player;
import players.mcts.MCTSPlayer;
import players.mcts.SingleTreeNode;
import players.optimisers.ParameterizedPlayer;
import utils.ElapsedCpuTimer;
import utils.Types;

import java.util.ArrayList;
import java.util.Random;

public class GroupXPlayer extends ParameterizedPlayer {

    private Random m_rnd;
    public Types.ACTIONS[] actions;
    public GroupXParams params;

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

        // TODO update gs
        if (gs.getGameMode().equals(Types.GAME_MODE.TEAM_RADIO)){
            int[] msg = gs.getMessage();
        }

        ElapsedCpuTimer ect = new ElapsedCpuTimer();
        ect.setMaxTimeMillis(params.num_time);

        // Number of actions available
        int num_actions = actions.length;

        // Root of the tree
        GroupXSingleTreeNode m_root = new GroupXSingleTreeNode(params, m_rnd, num_actions, actions);
        m_root.setRootGameState(gs);

        //Determine the action using MCTS...
        m_root.mctsSearch(ect);

        //Determine the best action to take and return it.
        int action = m_root.mostVisitedAction();

        // TODO update message memory

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
}
