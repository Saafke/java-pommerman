package players.groupx;

import core.GameState;
import players.heuristics.AdvancedHeuristic;
import players.heuristics.CustomHeuristic;
import players.heuristics.StateHeuristic;
import utils.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
// MB: BRANCH
public class GroupXSingleTreeNode
{
    public GroupXParams params;
    public GroupXutils utilsX;

    private GroupXSingleTreeNode parent;
    private GroupXSingleTreeNode[] children;
    private double totValue;
    private int nVisits;
    private Random m_rnd;
    private int m_depth;
    private double[] bounds = new double[]{Double.MAX_VALUE, -Double.MAX_VALUE};
    private int childIdx;
    private int fmCallsCount;

    private int num_actions;
    private Types.ACTIONS[] actions;

    private GameState rootState;
    private StateHeuristic rootStateHeuristic;

    GroupXSingleTreeNode(GroupXParams p, GroupXutils utilsX, Random rnd, int num_actions, Types.ACTIONS[] actions) {
        this(p, utilsX, null, -1, rnd, num_actions, actions, 0, null);
    }

    private GroupXSingleTreeNode(GroupXParams p, GroupXutils utilsX, GroupXSingleTreeNode parent, int childIdx,
                                 Random rnd, int num_actions, Types.ACTIONS[] actions, int fmCallsCount,
                                 StateHeuristic sh) {
        this.params = p;
        this.utilsX = utilsX;
        this.fmCallsCount = fmCallsCount;
        this.parent = parent;
        this.m_rnd = rnd;
        this.num_actions = num_actions;
        this.actions = actions;
        children = new GroupXSingleTreeNode[num_actions];
        totValue = 0.0;
        this.childIdx = childIdx;
        if(parent != null) {
            m_depth = parent.m_depth + 1;
            this.rootStateHeuristic = sh;
        }
        else
            m_depth = 0;
    }

    void setRootGameState(GameState gs)
    {
        this.rootState = gs;
        if (params.heuristic_method == params.CUSTOM_HEURISTIC)
            this.rootStateHeuristic = new CustomHeuristic(gs);
        else if (params.heuristic_method == params.ADVANCED_HEURISTIC) // New method: combined heuristics
            this.rootStateHeuristic = new AdvancedHeuristic(gs, m_rnd);
    }


    void mctsSearch(ElapsedCpuTimer elapsedTimer) {

        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining;
        int numIters = 0;

        int remainingLimit = 5;
        boolean stop = false;

        while(!stop){

            GameState state = rootState.copy();
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
            GroupXSingleTreeNode selected = treePolicy(state);
            double delta = selected.rollOut(state);
            backUp(selected, delta);

            //Stopping condition
            if(params.stop_type == params.STOP_TIME) {
                numIters++;
                acumTimeTaken += (elapsedTimerIteration.elapsedMillis()) ;
                avgTimeTaken  = acumTimeTaken/numIters;
                remaining = elapsedTimer.remainingTimeMillis();
                stop = remaining <= 2 * avgTimeTaken || remaining <= remainingLimit;
            }else if(params.stop_type == params.STOP_ITERATIONS) {
                numIters++;
                stop = numIters >= params.num_iterations;
            }else if(params.stop_type == params.STOP_FMCALLS)
            {
                fmCallsCount+=params.rollout_depth;
                stop = (fmCallsCount + params.rollout_depth) > params.num_fmcalls;
            }
        }
        //System.out.println(" ITERS " + numIters);
    }

    // Node selection
    private GroupXSingleTreeNode treePolicy(GameState state) {

        GroupXSingleTreeNode cur = this;

        while (!state.isTerminal() && cur.m_depth < params.rollout_depth)
        {
            if (cur.notFullyExpanded()) {
                return cur.expand(state);

            } else {
                cur = cur.uct(state);
            }
        }

        return cur;
    }

    // Expansion
    private GroupXSingleTreeNode expand(GameState state) {

        int bestAction = 0;
        double bestValue = -1;

        //loop over all children - possible actions
        for (int i = 0; i < children.length; i++) {
            //get a random double between 0 and 1
            double x = m_rnd.nextDouble();

            if (x > bestValue && children[i] == null) {
                bestAction = i;
                bestValue = x;
            }
        }

        //Roll the state
        roll(state, actions[bestAction]);

        GroupXSingleTreeNode tn = new GroupXSingleTreeNode(params, utilsX, this,bestAction,this.m_rnd,num_actions,
                actions, fmCallsCount, rootStateHeuristic);
        children[bestAction] = tn;
        return tn;
    }

    private void roll(GameState gs, Types.ACTIONS act)
    {
        //Simple, all random first, then my position.
        int nPlayers = 4;
        Types.ACTIONS[] actionsAll = new Types.ACTIONS[4];
        int playerId = gs.getPlayerId() - Types.TILETYPE.AGENT0.getKey();

        //XW: get the IDs of all living enemies
        ArrayList<Types.TILETYPE> enemyIDs = gs.getAliveEnemyIDs();

        for(int i = 0; i < nPlayers; ++i)
        {
            if(playerId == i)
            {
                actionsAll[i] = act;
            }else {
                // MB: Use Table lookup here

                //XW: original code - assigns random actions for enemies
                //int actionIdx = m_rnd.nextInt(gs.nActions());
                //actionsAll[i] = Types.ACTIONS.all().get(actionIdx);

                //XW: get RELEVANT enemy
                Types.TILETYPE curEnemy = null;
                for ( Types.TILETYPE t : enemyIDs){
                    if ((t.getKey() - Types.TILETYPE.AGENT0.getKey()) == i){
                        curEnemy = t;
                    }
                }

                // XW: if enemy not find in AliveEnemies, this enemy is dead and just assign stop
                if (curEnemy == null){
                    actionsAll[i] = Types.ACTIONS.all().get(0);
                    continue; //next iteration
                }

                //System.out.println("Current Enemy = "+curEnemy+ " with i="+i);
                // todo: fix bug here somewhere - sometimes output = "Couldn't find enemy position?? whyy oo whyy
                //XW: get enemy's position
                Vector2d enemyPos = utilsX.findEnemyPosition(gs.getBoard(), curEnemy);

                //XW: get enemy's surroundings
                int enemySurroundings = utilsX.getSurroundingsIndex(enemyPos, gs.getBoard());

                //XW: get the actionDistribution corresponding to those particular surroundings -
                // -- get actionDistribution hashmap
                HashMap<Integer, ActionDistribution> actionDistributions = utilsX.actionDistributionsMCTS;
                //-- sample action - or random if surrounding not in hashmap
                int actionIdx;
                if(actionDistributions.containsKey(enemySurroundings)){
                    actionIdx = actionDistributions.get(enemySurroundings).sampleAction();
                }else{
                    actionIdx = m_rnd.nextInt(gs.nActions());
                }

                //XW: set action
                actionsAll[i] = Types.ACTIONS.all().get(actionIdx);
            }
        }

        gs.next(actionsAll);

    }

    // Upper bound policy UCB1
    private GroupXSingleTreeNode uct(GameState state) {
        GroupXSingleTreeNode selected = null;
        double bestValue = -Double.MAX_VALUE;
        for (GroupXSingleTreeNode child : this.children)
        {
            double hvVal = child.totValue;
            double childValue =  hvVal / (child.nVisits + params.epsilon);

            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

            double uctValue = childValue +
                    params.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + params.epsilon));

            uctValue = Utils.noise(uctValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly

            // small sampleRandom numbers: break ties in unexpanded nodes
            if (uctValue > bestValue) {
                selected = child;
                bestValue = uctValue;
            }
        }
        if (selected == null)
        {
            throw new RuntimeException("Warning! returning null: " + bestValue + " : " + this.children.length + " " +
                    + bounds[0] + " " + bounds[1]);
        }

        //Roll the state:
        roll(state, actions[selected.childIdx]);

        return selected;
    }

    // Simulation - rollout
    private double rollOut(GameState state)
    {
        int thisDepth = this.m_depth;

        while (!finishRollout(state,thisDepth)) {
            int action = safeRandomAction(state);
            roll(state, actions[action]);
            thisDepth++;
        }

        return rootStateHeuristic.evaluateState(state);
    }

    // Get a random action that does not kill itself.
    private int safeRandomAction(GameState state)
    {
        Types.TILETYPE[][] board = state.getBoard();
        ArrayList<Types.ACTIONS> actionsToTry = Types.ACTIONS.all();
        int width = board.length;
        int height = board[0].length;

        while(actionsToTry.size() > 0) {

            int nAction = m_rnd.nextInt(actionsToTry.size());
            Types.ACTIONS act = actionsToTry.get(nAction);
            Vector2d dir = act.getDirection().toVec();

            Vector2d pos = state.getPosition();
            int x = pos.x + dir.x;
            int y = pos.y + dir.y;

            if (x >= 0 && x < width && y >= 0 && y < height)
                if(board[y][x] != Types.TILETYPE.FLAMES)
                    return nAction;

            actionsToTry.remove(nAction);
        }

        //Uh oh...
        return m_rnd.nextInt(num_actions);
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean finishRollout(GameState rollerState, int depth)
    {
        if (depth >= params.rollout_depth)      //rollout end condition.
            return true;

        if (rollerState.isTerminal())               //end of game
            return true;

        return false;
    }

    // Back Propagation
    private void backUp(GroupXSingleTreeNode node, double result)
    {
        GroupXSingleTreeNode n = node;
        while(n != null)
        {
            n.nVisits++;
            n.totValue += result;
            if (result < n.bounds[0]) {
                n.bounds[0] = result;
            }
            if (result > n.bounds[1]) {
                n.bounds[1] = result;
            }
            n = n.parent;
        }
    }


    int mostVisitedAction() {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;
        boolean allEqual = true;
        double first = -1;

        for (int i=0; i<children.length; i++) {

            if(children[i] != null)
            {
                if(first == -1)
                    first = children[i].nVisits;
                else if(first != children[i].nVisits)
                {
                    allEqual = false;
                }

                double childValue = children[i].nVisits;
                childValue = Utils.noise(childValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1)
        {
            selected = 0;
        }else if(allEqual)
        {
            //If all are equal, we opt to choose for the one with the best Q.
            selected = bestAction();
        }

        return selected;
    }

    private int bestAction()
    {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;

        for (int i=0; i<children.length; i++) {

            if(children[i] != null) {
                double childValue = children[i].totValue / (children[i].nVisits + params.epsilon);
                childValue = Utils.noise(childValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1)
        {
            System.out.println("Unexpected selection!");
            selected = 0;
        }

        return selected;
    }


    private boolean notFullyExpanded() {
        for (GroupXSingleTreeNode tn : children) {
            if (tn == null) {
                return true;
            }
        }

        return false;
    }
}
