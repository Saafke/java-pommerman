package utils;

import utils.Types;
import java.io.*;

public class ActionDistribution implements Serializable  {
    private int[] actionCounts;

    public ActionDistribution() {
        actionCounts = new int[]{0,0,0,0,0,0};
    }
    public int getActionCount(Types.ACTIONS action){
        return actionCounts[action.getKey()];
    }

    public void updateActionCount(Types.ACTIONS action){
        actionCounts[action.getKey()] += 1;
    }

    public String toString(){
        return String.format("STOP: %d, UP: %d, DOWN: %d, LEFT: %d, RIGHT: %d, BOMB: %d",
                actionCounts[0],actionCounts[1],actionCounts[2],actionCounts[3],actionCounts[4],actionCounts[5]);
    }

}