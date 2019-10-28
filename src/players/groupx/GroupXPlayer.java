package players.groupx;

import core.GameState;
import players.Player;
import players.mcts.MCTSPlayer;
import players.mcts.SingleTreeNode;
import players.optimisers.ParameterizedPlayer;
import utils.ElapsedCpuTimer;
import utils.Types;
import utils.Vector2d;

import java.util.ArrayList;
import java.util.Random;

public class GroupXPlayer extends ParameterizedPlayer {
    // AGENT0, AGENT1
    private Random m_rnd;
    public Types.ACTIONS[] actions;
    public GroupXParams params;
    //MB: Temporary test of storing AGENT0Moves.
    private Vector2d[] enemyPositions = new Vector2d[3];
    private int[] enemyMoves = new int[800];
    private int enemyX = 0;
    private int enemyY = 0;

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
        //MB: Will likely need to infer the actions that opponents took...

        // Returns, for example: AGENT0, AGENT1, AGENT3
        Types.TILETYPE[] enemies = gs.getEnemies();
        Types.TILETYPE[][] board = gs.getBoard();
        
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

    private int findEnemyPositions(Types.TILETYPE[][] board){
        return 0;
    }

    private int updateEnemyActions(Types.TILETYPE[][] board, int ex, int ey){
        int width = board.length;
        int height = board[0].length;

        return 0;
    }
}
