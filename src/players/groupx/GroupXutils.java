package players.groupx;

import utils.ActionDistribution;
import utils.Types;
import utils.Vector2d;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Contains all the relevant functions for the GroupXpommerdude
 */
public class GroupXutils {

    // XW: Empty constructor
    public GroupXutils(){
        //
    }

    Integer[] surroundingsMap = new Integer[]{
            1,2,3,4,5,1,6,6,6,7,7,7,7,7
    };

    HashMap<Integer, ActionDistribution> actionDistributionsMCTS = retrieveActionDistributions("hashMapMCTS.ser");
    //HashMap<Integer, ActionDistribution> actionDistributions2 = retrieveActionDistributions("hashMapMCTS.ser");

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
        System.out.println("Error: Couldn't find enemy position "+enemy);
        return new Vector2d(0,0);
    }

    // If we know where the enemy was the last tick, we only need to check a few places (more efficient than whole board search).
    //TODO: Implement faster findEnemyPosition method when the previous position is known, handling also when we'd exceed the board array bounds.
    public Vector2d findEnemyPosition(Types.TILETYPE[][] board,Types.TILETYPE enemy, Vector2d enemyPrevPos){
        if(board[enemyPrevPos.y][enemyPrevPos.x] == enemy){
            return new Vector2d(enemyPrevPos.x, enemyPrevPos.y);
        }
        if(board[enemyPrevPos.y+1][enemyPrevPos.x] == enemy){
            return new Vector2d(enemyPrevPos.x, enemyPrevPos.y+1);
        }
        return null;
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

    private HashMap<Integer, ActionDistribution> retrieveActionDistributions(String hashMapFilename){

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
//        Set set = map.entrySet();
//        Iterator iterator = set.iterator();
//        while(iterator.hasNext()) {
//            Map.Entry mentry = (Map.Entry)iterator.next();
//            System.out.print("key: "+ mentry.getKey() + " & Value: ");
//            System.out.println(mentry.getValue());
//        }

        return map;
    }
}
