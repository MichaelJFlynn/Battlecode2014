package darkpurple3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Random;

import battlecode.common.*;

public class RobotPlayer {

	static Direction allDirections[] = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
		Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST
	};
	static Direction wall_hug_dir = null;
	static MapLocation target;
	static Random randy = new Random();
	static ArrayList<Direction> path = null;
	static Iterator<Direction> path_iterator = null;
	static RobotController rcf;
	static ArrayList<MapLocation> corners = null;
	static boolean explored[][];
	static double cow_growth[][];
	static String goal = "explore";
	static ArrayList<MapLocation> pastures = new ArrayList<MapLocation>();
	static boolean setting_up_tower = false;
	static MapLocation tower_location = null;
	static int noisetower_num = 0;
	static int CONSTRUCT_CHANNEL = 100;
	
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
				
				if(rc.getType() == RobotType.NOISETOWER) {
					runTower(rc);
				}
			} catch(GameActionException e) {
				e.printStackTrace();
			}
			rc.yield();
		}
		
	}

	public static void runSoldier(RobotController rcs) throws GameActionException {
		if(rcs.isActive()) {
			if(setting_up_tower) {
				rcf.setIndicatorString(0, "Setting up Tower");
				if(!rcf.getLocation().equals(tower_location)) {
					if(!construction_free(tower_location, RobotType.NOISETOWER, 2)) {
						rcf.setIndicatorString(1, "Tower Cancelled");
						rcf.broadcast(0, rcf.readBroadcast(0) - 1);
						setting_up_tower = false;
					} else {
						rcf.setIndicatorString(1, "Moving to Tower Location: " + tower_location);
						move_to(tower_location);
					}
				} else {
					rcf.setIndicatorString(1, "Constructing tower");
					broadcast_tower(rcf.getLocation());
					rcf.construct(RobotType.NOISETOWER);
				}
				return;
			}
			// attack enemies
			Robot[] enemies = rcs.senseNearbyGameObjects(Robot.class, RobotType.SOLDIER.attackRadiusMaxSquared*2, rcs.getTeam().opponent());
			Robot[] friends = rcs.senseNearbyGameObjects(Robot.class, RobotType.SOLDIER.attackRadiusMaxSquared*2, rcs.getTeam());
			int num_baddies = 0;
			for(Robot enemy: enemies) {
				if(rcf.senseRobotInfo(enemy).type == RobotType.SOLDIER) num_baddies++;
			}
			enemies = removeHQ(enemies);
			if(enemies.length > 0) {
				rcf.setIndicatorString(0, "Fighting enemies");
				MapLocation enemy_loc = rcs.senseLocationOf(enemies[0]);
				if(rcs.canAttackSquare(enemy_loc)) {
					rcf.setIndicatorString(1, "Shooting enemies");
					rcs.attackSquare(enemy_loc);
				}
				else {
					//if out-numbered run away
					if(friends.length + 1 <= num_baddies){
						rcf.setIndicatorString(1, "Conservative");
						run_away(enemy_loc);
					} else {
						//attack!
						rcf.setIndicatorString(1, "Aggressive");
						move_to(enemy_loc);
					}
				}
				return;
			} 
			// explore the map, looking for high cow counts
			rcf.setIndicatorString(0, "Exploring");
			update_explored();
			navigate_controller(rcs, rcs.senseEnemyHQLocation());
		}
	}
	
	public static void runTower(RobotController rct) throws GameActionException {
		// find mapLocations that are pastr-radius + scare-radius away from pastures
		MapLocation[] pastures = rcf.sensePastrLocations(rcf.getTeam());
		ArrayList<MapLocation> nearby_pastures = new ArrayList<MapLocation>(pastures.length);
		for(MapLocation loc : pastures) {
			if(loc.distanceSquaredTo(rct.getLocation()) < RobotType.NOISETOWER.attackRadiusMaxSquared) {
				nearby_pastures.add(loc);
			}
		}
		rcf.setIndicatorString(0, "Finding targets");
		ArrayList<MapLocation> good_targets = new ArrayList<MapLocation>();
		for(MapLocation loc : nearby_pastures) {
			MapLocation[] targets = MapLocation.getAllMapLocationsWithinRadiusSq(loc, GameConstants.ATTACK_SCARE_RANGE + RobotType.PASTR.sensorRadiusSquared);
			for(MapLocation target_loc : targets) {
				if(target_loc.distanceSquaredTo(loc) >= RobotType.PASTR.sensorRadiusSquared + GameConstants.ATTACK_SCARE_RANGE) {
					good_targets.add(target_loc);
				}
			}
		}
		boolean shot = false;
		while(!shot) {
			if(good_targets.size() > 0) {
				int randomindex = randy.nextInt(good_targets.size());
				if(rcf.canAttackSquare(good_targets.get(randomindex))) {
					shot = true;
					rcf.setIndicatorString(1, "Attacking: " + good_targets.get(randomindex));
					rcf.attackSquare(good_targets.get(randomindex));
				} else {
					good_targets.remove(randomindex);
				}
			} else {
				rct.setIndicatorString(1, "Can't find good targets");
				break;
			}
		} 
	}
	
	public static boolean construction_free(MapLocation m, RobotType t, int multiplier) throws GameActionException {
		int current_channel = CONSTRUCT_CHANNEL;
		int broadc = rcf.readBroadcast(current_channel);
		while(broadc != 0) {
			int y = broadc % 100;
			int x = (broadc/100) % 100;
			int code = broadc/(100*100);
			if(code == 1 && t.equals(RobotType.PASTR)) {
				// make sure we are out of range
				if(m.distanceSquaredTo(new MapLocation(x,y)) < multiplier*RobotType.PASTR.sensorRadiusSquared) {
					return false;
				}
			}
			if(code ==2 && t.equals(RobotType.NOISETOWER)) {
				// make sure we are out of range
				if(m.distanceSquaredTo(new MapLocation(x,y)) < multiplier*RobotType.NOISETOWER.attackRadiusMaxSquared){ 
					return false;
				}
			}
			current_channel++;
			broadc = rcf.readBroadcast(current_channel);
		}
		return true;
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
	
	public static void run_away(MapLocation m) throws GameActionException {
		int max_distance = -1;
		Direction max_dir = Direction.NONE;
		for(Direction dir : allDirections) { 
			int dist = m.distanceSquaredTo(rcf.getLocation().add(dir));
			if(dist > max_distance && rcf.canMove(dir)) {
				max_distance = dist;
				max_dir = dir;
			}
		}
		rcf.move(max_dir);
	}
	
	public static void move_to(MapLocation m) throws GameActionException {
		int min_distance = 9999999;
		Direction min_dir = Direction.NONE;
		for(Direction dir : allDirections) { 
			int dist = m.distanceSquaredTo(rcf.getLocation().add(dir));
			if(dist < min_distance && rcf.canMove(dir)) {
				min_distance = dist;
				min_dir = dir;
			}
		}
		if(min_dir != Direction.NONE) { 
			rcf.move(min_dir);
		}
	}
	
	public static void update_explored() {
		// for all map locations in sight
		int sensor_radius_sq = RobotType.SOLDIER.sensorRadiusSquared;
		MapLocation[] locations = MapLocation.getAllMapLocationsWithinRadiusSq(rcf.getLocation(), (int) (sensor_radius_sq));
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
		pastures = new ArrayList<MapLocation>();
		noisetower_num = 0;
		ArrayList<MapLocation> high_cows = sense_high_cows();
		if(pastures.size() >= 2 && rcf.readBroadcast(0) < 2 && noisetower_num < 1 && construction_free(rcf.getLocation(), RobotType.NOISETOWER, 4)) {
			System.out.println("I'm looking to set up a tower!");
			rcf.broadcast(0, rcf.readBroadcast(0) + 1);
			setting_up_tower = true;
			int avgx = 0;
			int avgy = 0;
			for(MapLocation past : pastures) {
				avgx += past.x;
				avgy += past.y;
			}
			avgx /= pastures.size();
			avgy /= pastures.size();
			tower_location = new MapLocation(avgx, avgy);
			if(rcf.senseObjectAtLocation(tower_location)==null) {
				return;
			} else {
				for(Direction dir : allDirections) {
					if(rcf.senseObjectAtLocation(tower_location.add(dir)) ==null) {
						tower_location = tower_location.add(dir);
						System.out.println("I found a place for the tower!");
						return;
					}
				}
				System.out.println("I couldn't find a place for the tower!");
				setting_up_tower = false;
			}
		}
		if(high_cows.size() > 0) {
			path = null;
			path_iterator = null;
			MapLocation future_pasture = high_cows.get(0);
			if(!future_pasture.equals(rcf.getLocation())) {
				sneak_to_closest_tile(future_pasture);
			} else {
				broadcast_pastr(rcf.getLocation());
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
	
	public static void broadcast_pastr(MapLocation m) throws GameActionException {
		int current_channel = CONSTRUCT_CHANNEL;
		boolean broadcasted = false;
		while(!broadcasted) {
			if(rcf.readBroadcast(current_channel) == 0) {
				rcf.broadcast(current_channel, 1*100*100 + m.x*100 + m.y);
				broadcasted = true;
			} else {
				current_channel++;
			}
		}
	}
	
	public static void broadcast_tower(MapLocation m) throws GameActionException {
		int current_channel = CONSTRUCT_CHANNEL;
		boolean broadcasted = false;
		while(!broadcasted) {
			if(rcf.readBroadcast(current_channel) == 0) {
				rcf.broadcast(current_channel, 2*100*100 + m.x*100 + m.y);
				broadcasted = true;
			} else {
				current_channel++;
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
		MapLocation[] pasturez = rcf.sensePastrLocations(rcf.getTeam());
		ArrayList<MapLocation> high_cows = new ArrayList<MapLocation>(locations.length);
		for(MapLocation loc : locations) {
			GameObject go = rcf.senseObjectAtLocation(loc);
			if(go != null && rcf.senseRobotInfo((Robot) go).type== RobotType.NOISETOWER) {
				noisetower_num++;
			}
			for(MapLocation past : pasturez) {
				if(loc.equals(past)) {
					pastures.add(loc);
				}
			}
			if(rcf.senseCowsAtLocation(loc) > 800 && !in_range_of_pasture(loc, 4)) {
				if(!rcf.getLocation().equals(loc) && rcf.senseObjectAtLocation(loc) != null) {
					continue;
				}
				high_cows.add(loc);
			}
		}
		return high_cows;
	}
	
	public static boolean in_range_of_pasture(MapLocation m, int multiplier) throws GameActionException {
		MapLocation[] pastures = rcf.sensePastrLocations(rcf.getTeam());
		for(MapLocation p_loc : pastures) {
			if(p_loc.distanceSquaredTo(m) < multiplier*RobotType.PASTR.sensorRadiusSquared){
				return true;
			}
		}
		// get constructing pastures
		if(!construction_free(m, RobotType.PASTR, 4)) {
			return true;
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
			rcf.setIndicatorString(1, "Nodes expanded: " + visited.size());
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
