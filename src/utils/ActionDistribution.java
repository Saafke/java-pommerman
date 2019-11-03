package utils;

import utils.Types;
import java.io.*;
import java.util.Random;

public class ActionDistribution implements Serializable  {
    private int[] actionCounts;

    // 0:stop    1:up    2:down     3:left     4:right    5:bomb
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

    //Returns a sampled action from this particular ActionDistribution
    public int sampleAction(){

        //Count total number in distribution
        int sum=0;
        for (int i : actionCounts){
            sum += i;
        }

        // get random number between 0 and sum
        int rand = new Random().nextInt(sum);

        // get the random action
        int currentSum = 0;
        for( int i=0; i<=5; i++){
            currentSum += actionCounts[i];
            if( rand <= currentSum ){
                return i;
            }
        }
        return -1;
    }
}