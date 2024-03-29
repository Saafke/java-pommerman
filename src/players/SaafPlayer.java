package players;

import core.GameState;
import players.heuristics.AdvancedHeuristic;
import players.heuristics.CustomHeuristic;
import players.heuristics.StateHeuristic;
import utils.Types;
import utils.Utils;

import java.util.ArrayList;
import java.util.Random;

public class SaafPlayer extends Player{

    private Random random;
    private boolean rndOpponentModel;
    public double epsilon = 1e-6;
    private StateHeuristic rootStateHeuristic;

    public SaafPlayer(long seed, int id) {
        super(seed, id);
        random = new Random(seed);
    }

    @Override
    public Types.ACTIONS act(GameState gs) {

        rootStateHeuristic = new CustomHeuristic(gs);
        //rootStateHeuristic = new AdvancedHeuristic(gs, new Random() );

        rndOpponentModel = true;

        ArrayList<Types.ACTIONS> actionsList = Types.ACTIONS.all();
        double maxQ = Double.NEGATIVE_INFINITY;
        Types.ACTIONS bestAction = null;

        // first look-ahead
        for (Types.ACTIONS act1 : actionsList) {
            GameState gsCopy = gs.copy();
            rollRnd(gsCopy, act1);

            // second look-ahead
            for (Types.ACTIONS act2 : actionsList) {
                GameState gsCopy2 = gsCopy.copy();
                rollRnd(gsCopy2, act2);

                // third look-ahead
                for (Types.ACTIONS act3 : actionsList) {
                    GameState gsCopy3 = gsCopy2.copy();
                    rollRnd(gsCopy3, act3);

                    double valState1 = rootStateHeuristic.evaluateState(gsCopy);
                    double valState2 = rootStateHeuristic.evaluateState(gsCopy2);
                    double valState3 = rootStateHeuristic.evaluateState(gsCopy3);

                    double valState = valState1 + valState2 + valState3;
                    //System.out.println(valState);
                    double Q = Utils.noise(valState, this.epsilon, this.random.nextDouble());

                    //System.out.println("Action:" + action + " score:" + Q);
                    if (Q > maxQ) {
                        maxQ = Q;
                        bestAction = act1;
                    }
                }
            }
        }
        return bestAction;
    }

    @Override
    public int[] getMessage() {
        // default message
        return new int[Types.MESSAGE_LENGTH];
    }

    @Override
    public Player copy() {
        return new SaafPlayer(seed, playerID);
    }

    private void rollRnd(GameState gs, Types.ACTIONS act)
    {
        //Simple, all random first, then my position.
        int nPlayers = 4;
        Types.ACTIONS[] actionsAll = new Types.ACTIONS[4];

        for(int i = 0; i < nPlayers; ++i)
        {
            if(i == getPlayerID() - Types.TILETYPE.AGENT0.getKey())
            {
                actionsAll[i] = act;
            }else{
                if(rndOpponentModel){
                    int actionIdx = random.nextInt(gs.nActions());
                    actionsAll[i] = Types.ACTIONS.all().get(actionIdx);
                }else
                {
                    actionsAll[i] = Types.ACTIONS.ACTION_STOP;
                }
            }
        }

        gs.next(actionsAll);
    }
}
