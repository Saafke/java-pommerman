import core.Game;
import players.*;
import players.groupx.GroupXParams;
import players.groupx.GroupXPlayer;
import players.groupx.GroupXutils;
import utils.Types;
import players.rhea.utils.Constants;
import players.mcts.MCTSPlayer;
import players.mcts.MCTSParams;
import players.rhea.utils.RHEAParams;
import players.rhea.RHEAPlayer;

import java.util.ArrayList;

public class Test {

    public static void main(String[] args) {

        // Game parameters
        long seed = System.currentTimeMillis();
        int boardSize = Types.BOARD_SIZE;
        Types.GAME_MODE gameMode = Types.GAME_MODE.FFA;
        boolean useSeparateThreads = false;
        GroupXutils utilsX = new GroupXutils();

        Game game = new Game(seed, boardSize, Types.GAME_MODE.FFA,"123");

        // Key controllers for human player s (up to 2 so far).
        KeyController ki1 = new KeyController(true);
        KeyController ki2 = new KeyController(false);

        // Create players
        ArrayList<Player> players = new ArrayList<>();
        int playerID = Types.TILETYPE.AGENT0.getKey();

        MCTSParams mctsParams = new MCTSParams();
        mctsParams.stop_type = mctsParams.STOP_TIME;
        mctsParams.num_iterations = 400;
        mctsParams.rollout_depth = 15;
        mctsParams.heuristic_method = mctsParams.ADVANCED_HEURISTIC;

        GroupXParams groupxParams = new GroupXParams();
        groupxParams.stop_type = groupxParams.STOP_TIME;
        groupxParams.heuristic_method = groupxParams.ADVANCED_HEURISTIC;
        groupxParams.num_iterations = 400;
        groupxParams.rollout_depth = 15;

        RHEAParams rheaParams = new RHEAParams();
        rheaParams.budget_type = Constants.ITERATION_BUDGET;
        rheaParams.iteration_budget = 200;
        rheaParams.individual_length = 12;
        rheaParams.heurisic_type = Constants.CUSTOM_HEURISTIC;


        players.add(new GroupXPlayer(seed, playerID++, groupxParams, utilsX));
        players.add(new MCTSPlayer(seed, playerID++, mctsParams));
        players.add(new MCTSPlayer(seed, playerID++, mctsParams));
        players.add(new MCTSPlayer(seed, playerID++, mctsParams));
        //players.add(new SimplePlayer(seed, playerID++));
        //players.add(new SimplePlayer(seed, playerID++));
        //players.add(new RHEAPlayer(seed, playerID++, rheaParams));


        // Make sure we have exactly NUM_PLAYERS players
        assert players.size() == Types.NUM_PLAYERS : "There should be " + Types.NUM_PLAYERS +
                " added to the game, but there are " + players.size();

        //Assign players and run the game.
        game.setPlayers(players);

        //Run a single game with the players
        Run.runGame(game, ki1, ki2, useSeparateThreads);

        /* MB: Strategy Switcher function debugging
        HashMap<Integer, ActionDistribution> actionStrategy = new HashMap<Integer, ActionDistribution>();
        actionStrategy.put(1111,new ActionDistribution(new int[]{1,0,10,0,0,1}));
        actionStrategy.put(2222,new ActionDistribution(new int[]{10,0,0,0,0,0}));

        HashMap<Integer, ActionDistribution> actionHistory = new HashMap<Integer, ActionDistribution>();
        actionHistory.put(1111,new ActionDistribution(new int[]{2,0,0,0,0,0}));
        actionHistory.put(2222,new ActionDistribution(new int[]{10,0,0,0,0,0}));

        double similarity = utilsX.computeActionSimilarity(actionStrategy,actionHistory);
        double percentage = utilsX.computeActionPercentage(actionStrategy,actionHistory);
        System.out.println(percentage);
        */

        /* Uncomment to run the replay of the previous game: */
//        if (game.isLogged()){
//            Game replay = Game.getLastReplayGame();
//            Run.runGame(replay, ki1, ki2, useSeparateThreads);
//            assert(replay.getGameState().equals(game.getGameState()));
//        }


        /* Run with no visuals, N Times: */
//        int N = 20;
//        Run.runGames(game, new long[]{seed}, N, useSeparateThreads);

    }

}
