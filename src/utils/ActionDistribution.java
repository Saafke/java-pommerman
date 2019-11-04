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
    //MB: If you want to initialise straight away for testing purposes. Unsafe, need right amount of entries
    public ActionDistribution(int[] c) {
        actionCounts = c;
    }
    public int getActionCount(Types.ACTIONS action){
        return actionCounts[action.getKey()];
    }

    public int getActionCount(int action){
        return actionCounts[action];
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

        // get random number between 0 and sum
        int rand = new Random().nextInt(sum());

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

    public int sum(){
        // Sum all elements in this ActionDistribution
        int sumThis=0;
        for (int i : actionCounts){
            sumThis += i;
        }
        return sumThis;
    }

    public double magnitude(){
        // Sum all elements in this ActionDistribution
        int sumThis=0;
        for (int i : actionCounts){
            sumThis += Math.pow(i, 2);
        }
        return Math.sqrt(sumThis);
    }
}