package darkpurple2;

import java.util.ArrayList;
import java.util.HashMap;


import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Random;

import battlecode.common.*;

public class RobotPlayer {

	static Direction allDirections[] = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
		Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST
	};
	static Direction wall_hug_dir = null;
	static HashMap<Robot, MapLocation> pastures = new HashMap<Robot, MapLocation>(); 
	static MapLocation target;
	static Random randy = new Random();
	static ArrayList<Direction> path = null;
	static Iterator<Direction> path_iterator = null;
	static RobotController rcf;
	static ArrayList<MapLocation> corners = null;
	static boolean explored[][];
	static double cow_growth[][];
	static String goal = "explore";
	
	public static void run(RobotController rc) {
		System.out.println("Hooah!");
		explored = new boolean[rc.getMapWidth()][rc.getMapWidth()];
		//target = new MapLocation(0, 0);
		randy.setSeed(rc.getRobot().getID());
		rcf = rc;
		cow_growth = rc.senseCowGrowth();
		target = find_fertile_ground();
		while(true){
			try {
				if(rc.getType()==RobotType.HQ) {
					runHQ(rc);
				}

				if(rc.getType() == RobotType.SOLDIER) {
					runSoldier(rc);
				}
			} catch(GameActionException e) {
				e.printStackTrace();
			}
			rc.yield();
		}
		
	}

	public static void runSoldier(RobotController rcs) throws GameActionException {
		if(rcs.isActive()) {
			// attack enemies
			Robot[] enemies = rcs.senseNearbyGameObjects(Robot.class, 10, rcs.getTeam().opponent());
			enemies = removeHQ(enemies);
			if(enemies.length > 0) {
				MapLocation enemy_loc = rcs.senseLocationOf(enemies[0]);
				if(rcs.canAttackSquare(enemy_loc)) {
					rcs.attackSquare(enemy_loc);
				}
				else navigate_controller(rcs, enemy_loc);
				return;
			} 
			// explore the map, looking for high cow counts
			update_explored();
			navigate_controller(rcs, rcs.senseEnemyHQLocation());
			
		}
	}
	
	public static Robot[] removeHQ(Robot[] robots) throws GameActionException {
		ArrayList<Robot> new_robots = new ArrayList<Robot>();
		for(int i=0; i < robots.length; i++) {
			if(!(rcf.senseRobotInfo(robots[i]).type == RobotType.HQ)) {
				new_robots.add(robots[i]);
			}
		}
		Robot[] ret = new Robot[new_robots.size()];
		for(int i=0; i< new_robots.size(); i++) {
			ret[i] = new_robots.get(i);
		}
		return ret;
	}
	
	public static void update_explored() {
		// for all map locations in sight
		int sensor_radius_sq = RobotType.SOLDIER.sensorRadiusSquared;
		MapLocation[] locations = MapLocation.getAllMapLocationsWithinRadiusSq(rcf.getLocation(), (int) Math.sqrt(sensor_radius_sq));
		for(MapLocation loc : locations) {
			//System.out.println("loc: " + loc.x + " " + loc.y);
			if(rcf.senseTerrainTile(loc) != TerrainTile.OFF_MAP){ 
				explored[loc.x][loc.y] = true;
			}
		}
	}
	 
	public static MapLocation find_fertile_ground() {
		int max_x = -1;
		int max_y = -1;
		double max_growth = -1;
		for(int x =0; x < rcf.getMapWidth(); x++) {
			for(int y = 0; y < rcf.getMapHeight(); y++) {
				if(cow_growth[x][y] > max_growth) {
					max_growth = cow_growth[x][y];
					max_x = x;
					max_y = y;
				}
			}
		}
		return new MapLocation(max_x, max_y);
	}
	
	public static void navigate_controller(RobotController rcs, MapLocation loc) throws GameActionException{
		/*if(corners == null) {
			download_corners();
		}*/
		ArrayList<MapLocation> high_cows = sense_high_cows();
		if(high_cows.size() > 0) {
			path = null;
			path_iterator = null;
			MapLocation future_pasture = high_cows.get(0);
			if(!future_pasture.equals(rcf.getLocation())) {
				sneak_to_closest_tile(future_pasture);
			} else {
				rcf.construct(RobotType.PASTR);
			}
			
		} else {
			if(path == null || path_iterator == null) {
				path = a_star();
				path_iterator = path.iterator();
			}
			// take a step
			if(!rcs.getLocation().equals(target) && path_iterator.hasNext()) {
				Direction nextStep = path_iterator.next();
				if(rcs.canMove(nextStep)) {
					rcs.move(nextStep);
				} else {
					path = null;
					path_iterator = null;
				}
			} else {
				path = null;
				path_iterator = null;
			}
		}
	}
	
	public static void sneak_to_closest_tile(MapLocation m) throws GameActionException {
		int min_dist = 999999;
		Direction min_dir = Direction.NONE;
		for(Direction dir : allDirections) {
			if(rcf.canMove(dir)) {
				int dist = m.distanceSquaredTo(rcf.getLocation().add(dir));
				if( dist < min_dist) {
					min_dist = dist;
					min_dir = dir;
				}
			}
		}
		rcf.sneak(min_dir);
	}
	
	public static ArrayList<MapLocation> sense_high_cows() throws GameActionException  {
		MapLocation[] locations = MapLocation.getAllMapLocationsWithinRadiusSq(rcf.getLocation(),  (RobotType.SOLDIER.sensorRadiusSquared));
		ArrayList<MapLocation> high_cows = new ArrayList<MapLocation>(locations.length);
		for(MapLocation loc : locations) {
			if(rcf.senseCowsAtLocation(loc) > 800 && !in_range_of_pasture(loc)) {
				if(!rcf.getLocation().equals(loc) && rcf.senseObjectAtLocation(loc) != null) {
					continue;
				}
				high_cows.add(loc);
			}
		}
		return high_cows;
	}
	
	public static boolean in_range_of_pasture(MapLocation m) {
		MapLocation[] pastures = rcf.sensePastrLocations(rcf.getTeam());
		for(MapLocation p_loc : pastures) {
			if(p_loc.distanceSquaredTo(m) <= RobotType.PASTR.sensorRadiusSquared){
				return true;
			}
		}
		return false;
	}
	
	// spawn Robots around HQ
	public static void runHQ(RobotController rch) throws GameActionException {
		if(rch.isActive()) {
			Robot[] enemies = rch.senseNearbyGameObjects(Robot.class, RobotType.HQ.attackRadiusMaxSquared, rch.getTeam().opponent());
			if(enemies.length > 0) {
				for(Robot enemy : enemies) {
					MapLocation enemy_loc = rch.senseRobotInfo(enemy).location;
					if(rch.canAttackSquare(enemy_loc)) {
						rch.attackSquare(enemy_loc);
					}
					break;
				}
			}
			
			/*if(corners == null) {
				define_corners();
				System.out.println("Corners: " + corners);
				upload_corners();
			} else {*/
				for(Direction dir : allDirections) {
					if(rch.canMove(dir) && rch.senseRobotCount() < GameConstants.MAX_ROBOTS){
						rch.spawn(dir);
						break;
					}
			//	}
			}
		}
	}
	
	
	public static void define_corners() {
		corners = new ArrayList<MapLocation>(20);
		for(MapLocation loc : MapLocation.getAllMapLocationsWithinRadiusSq(rcf.senseHQLocation(), rcf.getMapHeight()*rcf.getMapHeight() + rcf.getMapWidth() * rcf.getMapWidth())) {
			if(rcf.senseTerrainTile(loc) == TerrainTile.VOID) {
				int num_grass_tiles = 0; 
				for(Direction dir: allDirections) {
					if(rcf.senseTerrainTile(loc.add(dir)) == TerrainTile.NORMAL) {
						num_grass_tiles++;
					}
				}
				if(num_grass_tiles > 3) {
					corners.add(loc);
				}
			}
		}
	}

	// broadcast corner locations, incremented by 1 (0 is null character to signal end) 
	public static void upload_corners() throws GameActionException {
		int channel = 0;
		for(MapLocation corner : corners) {
			rcf.broadcast(channel, corner.x + 1);
			rcf.broadcast(channel + 1, corner.y + 1);
			channel += 2;
		}
		System.out.println("Broadcasted Corners");
	}
	
	public static void download_corners() throws GameActionException {
		corners = new ArrayList<MapLocation>(20);
		int channel = 0;
		int datax = rcf.readBroadcast(channel);
		int datay;
		while(datax != 0) {
			datay = rcf.readBroadcast(channel + 1);
			corners.add(new MapLocation(datax - 1, datay - 1));
			channel += 2;
			datax = rcf.readBroadcast(channel);
		}
		System.out.println("Recieved Corners: " + corners);
	}
	
	
	public static int manhattan_distance(MapLocation m, MapLocation t) {
		return Math.abs(m.x - t.x) + Math.abs(m.y - t.y);
	}
	
	
	// very raw a*
	// such beautiful code! Such wonderful side effects! 
	public static ArrayList<Direction> a_star() throws GameActionException {
		//int bytecode_start = Clock.getBytecodeNum();
		MapLocation start = rcf.getLocation();
		ArrayList<MapLocation> visited = new ArrayList<MapLocation>();
		PriorityQueue<Frontier> frontier_queue = new PriorityQueue<Frontier>();
		frontier_queue.add(new Frontier(start,new ArrayList<Direction>(), 0));
		Frontier current;
		//int bytecode_init = Clock.getBytecodeNum();
		//System.out.println("Bytecodes initializing astar: " + (bytecode_init - bytecode_start));
		while(!frontier_queue.isEmpty()) {
			// pop off the frontier queue
			current = frontier_queue.poll();
			//int queue_start = Clock.getBytecodeNum();
			if(visited.contains(current.current_loc)) {
				continue;
			}
			//System.out.println("adding to frontier took: " + (Clock.getBytecodeNum() - queue_start));
			if(!goal.equals("explore")) {
				if(current.current_loc.equals(target)) {
					System.out.println("Nodes evaluated: " + visited.size());
					return current.path;
				}
			} else {
				if(!explored[current.current_loc.x][current.current_loc.y]) {
					System.out.println("Nodes evaluated: " + visited.size());
					rcf.setIndicatorString(0,"Going to: " + current.current_loc);
					return current.path;
				}
			}
			// we only add to visited when we have explored the node.
			visited.add(current.current_loc);
			for(Direction dir : allDirections) {
				MapLocation neighbor = current.current_loc.add(dir);
				TerrainTile tile = rcf.senseTerrainTile(neighbor);
				// make sure this is a good neighbor to go to
				if(rcf.canSenseSquare(neighbor) && rcf.senseObjectAtLocation(neighbor) != null) {
					continue;
				}
				if(!visited.contains(neighbor) && !tile.equals(TerrainTile.VOID) && !tile.equals(TerrainTile.OFF_MAP)) {
					// add new direction to path
					ArrayList<Direction> newpath = new ArrayList<Direction>(current.path);
					newpath.add(dir);
					// calculate new cost, for now not taking account for roads
					int newcost = current.current_cost + 1;
					frontier_queue.add(new Frontier(neighbor, newpath, newcost));
				}
			}
			//int bytecode_eval = Clock.getBytecodeNum();
			//System.out.println("Bycodes in 1 evaluation: " + (bytecode_eval - bytecode_init) + ", " + current.current_loc);
			//bytecode_init = bytecode_eval;
		}
		return null;
	}
	
	private static class Frontier implements Comparable<Frontier> {
		// these are public because the class is private
		// and only used in a_star
		public ArrayList<Direction> path;
		public MapLocation current_loc;
		public int current_cost;
		
		public Frontier(MapLocation loc, ArrayList<Direction> cur_path, int cost) {
			current_loc = loc;
			path = cur_path;
			current_cost = cost;
		}

		@Override
		public int compareTo(Frontier other) {
			double mydist =  (current_cost*current_cost + current_loc.distanceSquaredTo(target));
			double otherdist = (other.current_cost*current_cost + other.current_loc.distanceSquaredTo(target));
			//System.out.println(current_loc + ", distance: " + mydist + " vs. " + other.current_loc + ", distance: " + otherdist);
			return (int) (mydist*100 - otherdist*100);
		}
		
	}
}
