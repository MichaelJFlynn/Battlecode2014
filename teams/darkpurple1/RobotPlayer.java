package darkpurple1;

import java.util.Random;

import battlecode.common.*;

public class RobotPlayer {
	
	public static void run(RobotController rc) {

		while(true){
			rc.yield();
			try {
				if(rc.getType()==RobotType.HQ) {
					
					Direction dir = Direction.NORTH;
						if(rc.isActive() && rc.canMove(dir) && rc.senseRobotCount() < GameConstants.MAX_ROBOTS){
							rc.spawn(dir);						
						} else {
							if(!rc.canMove(dir)) {
								dir = Direction.SOUTH;
							}
						}
				
				}
			
				if(rc.getType() == RobotType.SOLDIER) {
					Direction chosenDirection = Direction.NORTH; 
					if(rc.isActive() && rc.canMove(chosenDirection)){
						rc.move(chosenDirection);
					} else {
						if(rc.isActive()){
							rc.construct(RobotType.PASTR);
						}
					}
				}
			} catch(GameActionException e) {
				e.printStackTrace();
			}
				
		}
	}
}


