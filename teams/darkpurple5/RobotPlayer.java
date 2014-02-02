package darkpurple5;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

import battlecode.common.*;

public class RobotPlayer {

	static Direction allDirections[] = {Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
		Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST
	};
	static Direction bugDirections[] = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
	static Direction wall_hug_dir = null;
	static MapLocation target;
	static Random randy = new Random();
	static ArrayList<Direction> path = null;
	static Iterator<Direction> path_iterator = null;
	static RobotController rcf;
	static ArrayList<MapLocation> corners = null;
	//static boolean explored[][];
	static double cow_growth[][];
	static ArrayList<MapLocation> pastures = new ArrayList<MapLocation>();
	static boolean setting_up_tower = false;
	static MapLocation tower_location = null;
	static int noisetower_num = 0;
	static int CONSTRUCT_CHANNEL = 500;
	static MapLocation nearby_pastr;
	static ArrayList<MapLocation> shoot_places = null;
	static Iterator<MapLocation> shoot_iterator = null;
	static int ASTAR_LIMIT = 15;
	static int PATHS_MAX = 100;
	static boolean on_mission = false;
	static ArrayList<MapLocation> bug_path = null;
	static ArrayList<MapLocation> last_path = null;
	static MapLocation hq_loc;
	static double my_health;
	static int HQ_VIABILITY_CHANNEL = 499;
	static int PASTR_COUNT_CHANNEL = 498;
	static enum Order { 
		WAITING, GOTO,  ATTACK, DEFEND,  PASTR, NOISETOWER
	}
	static Order mission = Order.WAITING;
	static int current_channel;
	static boolean broadcasted_completion = false;
	static ArrayList<MapLocation> sweet_pasture_locations = null;
	static int pasture_index = 0;
	static boolean attacked = false;
	static MapLocation enemy_hq;
	static int maxclock = 0;
	private static class MapLocDist {
		private MapLocation m;
		private int dist;
		
		private MapLocDist(MapLocation loc, int distance) {
			m = loc;
			dist = distance;
		}
		
		public MapLocation getLocation() {
			return m;
		}
		
		public int getDist() {
			return dist;
		}
	}
	static Comparator<MapLocDist> distance_from_loc;
	static int attackRadius;
	static double sqrt_attackRadius;
	static boolean hq_viable;
	static ArrayList<MapLocation> fertile_ground;
	static int xfert = 0;
	static int yfert = 0;
	static MapLocation centerPoint;
	static double hq_real_attack_radius;

	
	public static void run(RobotController rc) {
		hq_loc = rc.senseHQLocation();
		enemy_hq = rc.senseEnemyHQLocation();
		//System.out.println("Hooah!");
		//target = new MapLocation(0, 0);
		randy.setSeed(rc.getRobot().getID());
		rcf = rc;
		centerPoint = findCenterPoint();
		my_health = rcf.getHealth();
		cow_growth = rc.senseCowGrowth();
		target = hq_loc;
		distance_from_loc = new Comparator<MapLocDist>() {
			public int compare(MapLocDist a, MapLocDist b) {
				return a.getDist() - b.getDist();
			}
		};
		attackRadius = RobotType.SOLDIER.attackRadiusMaxSquared;
		sqrt_attackRadius = Math.sqrt(attackRadius);
		hq_real_attack_radius = RobotType.HQ.attackRadiusMaxSquared + 2*Math.sqrt(RobotType.HQ.attackRadiusMaxSquared) + 1;
		
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
				if(rc.getType() == RobotType.PASTR) {
					runPASTR();
				}
			} catch(GameActionException e) {
				e.printStackTrace();
			}
			rc.yield();
		}
	}
	
	public static void runPASTR() throws GameActionException {
		if(!broadcasted_completion) {
			rcf.broadcast(PASTR_COUNT_CHANNEL, rcf.readBroadcast(PASTR_COUNT_CHANNEL) - 1);
			broadcasted_completion = true;
		}
	}
	
	public static MapLocation findCenterPoint() {
		int x = (hq_loc.x + enemy_hq.x)/2;
		int y = (hq_loc.y + enemy_hq.y)/2;
		MapLocation center = new MapLocation(x,y);
		while(!walkable_tile(center)) {
			center = center.add(hq_loc.directionTo(enemy_hq));
		}
		return center;
	}
	
	// executes move command
	public static void move_to(Direction preferred_direction, boolean sneak, MapLocation avoiding) throws GameActionException {
		Direction final_dir_left = preferred_direction;
		Direction final_dir_right = preferred_direction;
		// initialize to true by default, if we care, change them
		boolean leftavoidsHQ = true;
		boolean rightavoidsHQ = true;
		int tries = 0;
		do {
			if(avoiding != null) {
				leftavoidsHQ = (rcf.getLocation().add(final_dir_left).distanceSquaredTo(avoiding) > hq_real_attack_radius );
				rightavoidsHQ = (rcf.getLocation().add(final_dir_right).distanceSquaredTo(avoiding) > hq_real_attack_radius);
			}
			if(rcf.canMove(final_dir_left) && leftavoidsHQ) {
				if(sneak) {
					rcf.sneak(final_dir_left);
				} else {
					rcf.move(final_dir_left);
				}
				break;
			}
			if(rcf.canMove(final_dir_right) && rightavoidsHQ) {
				if(sneak) {
					rcf.sneak(final_dir_right);
				} else {
					rcf.move(final_dir_right);
				}
				break;
			}
			final_dir_right = final_dir_right.rotateRight();
			final_dir_left = final_dir_left.rotateLeft();
			tries++;
		} while(tries < 5);
	}

	
	public static void read_missions() throws GameActionException {
		int current_channel = CONSTRUCT_CHANNEL;
		int favorite_channel = -1;
		boolean changed = false;
		Order highest_priority = mission;
		int current_reading = rcf.readBroadcast(current_channel);
		int min_dist = 999999;
		int dist = min_dist + 1;
		int x = -1;
		int y = -1;
		Order order;
		while(current_reading != 0) {
			if(current_reading != -1) {
				order = Order.values()[current_reading/(100*100)];
				if(order.ordinal() > highest_priority.ordinal()) {
					highest_priority = order;
					favorite_channel = -1;
					min_dist = 99999;
					dist = min_dist + 1;
					changed = true;
				}
				if(changed && order.ordinal() == highest_priority.ordinal()) {
					x = (current_reading/100) % 100;
					y = current_reading % 100;
					dist = rcf.getLocation().distanceSquaredTo(new MapLocation(x,y));
					if(dist < min_dist) {
						min_dist = dist;
						favorite_channel = current_channel;
					}
				}
				
			}
			current_channel++;
			current_reading = rcf.readBroadcast(current_channel);
		}
		if(favorite_channel != -1) {
			current_reading = rcf.readBroadcast(favorite_channel);
			mission = Order.values()[current_reading/(100*100)];
			x = (current_reading / 100) % 100;
			y = current_reading % 100;
			target = new MapLocation(x,y);
			rcf.broadcast(favorite_channel, -1);
		}
	}
	
	public static void execute_mission() throws GameActionException {
		rcf.setIndicatorString(0, "Order: " + mission + ", target: " + target);
		switch(mission) {
		case PASTR:
			if(rcf.getLocation().equals(target)) {
				rcf.construct(RobotType.PASTR);						
			} else {
				move_to(bug_nav_next(target), true, enemy_hq);
			}
			return;
		case NOISETOWER:
			if(rcf.getLocation().equals(target)) {			
				rcf.construct(RobotType.NOISETOWER);
			} else {
				move_to(bug_nav_next(target), true, enemy_hq);
			}
			return;
		case ATTACK:
			if(rcf.getLocation().equals(target)) {
				if(rcf.senseNearbyGameObjects(Robot.class, RobotType.SOLDIER.sensorRadiusSquared, rcf.getTeam().opponent()).length == 0) {
					mission = Order.WAITING;
					target = find_closest_pasture(rcf.getLocation());
				}
			} else {
				move_to(bug_nav_next(target), true, enemy_hq);
			}
			return;
		case DEFEND:
			if(rcf.getLocation().equals(target)) {
				if(rcf.senseNearbyGameObjects(Robot.class, RobotType.SOLDIER.sensorRadiusSquared, rcf.getTeam().opponent()).length == 0) {
					mission = Order.WAITING;
					target = find_closest_pasture(rcf.getLocation());
				}
			} else {
				move_to(bug_nav_next(target), true, enemy_hq);
			}
			return;
		default:
			MapLocation[] enemy_pastures = rcf.sensePastrLocations(rcf.getTeam().opponent());
			if(enemy_pastures.length > 0) {
				move_to(bug_nav_next(enemy_pastures[0]), true, enemy_hq);
			} else {
				MapLocation[] enemy_broadcasters = rcf.senseBroadcastingRobotLocations(rcf.getTeam().opponent());
				if(enemy_broadcasters.length > 0) {
					move_to(bug_nav_next(enemy_broadcasters[0]), true, enemy_hq);
				} else {
					//move_to(bug_nav_next(centerPoint), true, enemy_hq);
					move_to(bug_nav_next(enemy_hq), true, enemy_hq);
				}
			}
			return;
		
		}
	}
	
	
	public static void runSoldier(RobotController rcs) throws GameActionException {
		if(rcs.isActive()) {
			
			if(battlecode()) return;
			if(mission == Order.WAITING || mission == Order.GOTO) read_missions();
			execute_mission();
			
			/*
			// first we check if hqNT is viable and if so build HQNT
			// should take rush distance into account
			// maybe send a scout?
			if(rcs.readBroadcast(1) == 0 && hq_tower_viable) {
				// first guy builds a tower
				rcs.broadcast(1, 1);
				rcf.setIndicatorString(1, "Constructing tower");
				broadcast_tower(rcf.getLocation());
				rcs.construct(RobotType.NOISETOWER);
				return;
			}
			
			// then build pasture
			if(rcs.readBroadcast(1) == 1 && hq_tower_viable) {
				rcs.broadcast(1,2);
				rcs.construct(RobotType.PASTR);
				return;
			}
			
			// then check if we are on a tower mission, if so, move to it and construct
			if(setting_up_tower) {
				rcf.setIndicatorString(0, "Setting up Tower");
				if(!rcf.getLocation().equals(tower_location)) {
					if(!construction_free(tower_location, RobotType.NOISETOWER, 1)) {
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
			
			
			// if we have reached critical mass at hq, move out to establish
			// new pasture on fertile ground
			if(rcs.senseNearbyGameObjects(Robot.class, 36, rcf.getTeam()).length > 8) {
				on_mission = true;
			}
			if(on_mission) {
				target = find_fertile_ground();
				if(target.distanceSquaredTo(rcf.getLocation()) < 100) {
					MapLocation m = find_closest_pasture(rcf.getLocation());
					if(m != null && m.distanceSquaredTo(rcf.getLocation()) < 100) {
						setting_up_tower = true;
						tower_location = m.add(Direction.NORTH);
					}
				}
				
			}
			
			
			// explore the map? looking for high cow counts
			rcf.setIndicatorString(0, "Exploring");
			update_explored();
			navigate_controller(rcs, rcs.senseEnemyHQLocation());
			*/
		}
	}
	
	
	/*
	public static boolean battlecode() throws GameActionException {
		// attack enemies
		// battlecode!
		
		// free, get health
		my_health = rcf.getHealth();
		
		// 200 bytecodes, pretty necessary
		Robot[] enemies = rcf.senseNearbyGameObjects(Robot.class, RobotType.SOLDIER.sensorRadiusSquared, rcf.getTeam().opponent());
		Robot[] friends = rcf.senseNearbyGameObjects(Robot.class, RobotType.SOLDIER.sensorRadiusSquared, rcf.getTeam());
		
		// priorityQueues for sorting allies, enemies, and targets by distance
		PriorityQueue<MapLocDist> allies = new PriorityQueue<MapLocDist>(20, distance_from_loc);
		PriorityQueue<MapLocDist> baddies = new PriorityQueue<MapLocDist>(20, distance_from_loc);
		PriorityQueue<MapLocDist> targets = new PriorityQueue<MapLocDist>(20, distance_from_loc);
		
		// sort the robots into the priority queues
		MapLocation bad_hq_loc = null;
		MapLocation temp_loc;
		RobotType rob_type;
		for(Robot enemy: enemies) {
			rob_type = rcf.senseRobotInfo(enemy).type;
			temp_loc = rcf.senseLocationOf(enemy);
			if(rob_type == RobotType.SOLDIER) {
				baddies.add(new MapLocDist(temp_loc, rcf.getLocation().distanceSquaredTo(temp_loc)));
			}
		
			if(rob_type == RobotType.NOISETOWER) {
				targets.add(new MapLocDist(temp_loc, rcf.getLocation().distanceSquaredTo(temp_loc)));
			}
			if(rob_type == RobotType.PASTR) {
				targets.add(new MapLocDist(temp_loc, rcf.getLocation().distanceSquaredTo(temp_loc)));
			}
			if(rob_type == RobotType.HQ) {
				bad_hq_loc = temp_loc;
			}
		}
		for(Robot friend: friends) {
			rob_type = rcf.senseRobotInfo(friend).type;
			temp_loc = rcf.senseLocationOf(friend);
			if(rob_type == RobotType.SOLDIER) {
				allies.add(new MapLocDist(temp_loc, rcf.getLocation().distanceSquaredTo(temp_loc)));
			}
			//if(rob_type == RobotType.NOISETOWER) num_defends++;
			//if(rob_type == RobotType.PASTR) num_defends++;
			//if(rob_type == RobotType.HQ) good_hq_loc = temp_loc;
		}


		if(baddies.size() + targets.size() > 0) {
			

			// find closest enemy, target, ally
			MapLocDist enemy_loc;
			MapLocDist friend_loc;
			MapLocDist target_loc;
			if(baddies.size() > 0) {
				enemy_loc = baddies.peek();
			} else {
				enemy_loc = null;
			}
			if(allies.size() > 0) {
				friend_loc = allies.peek();
			} else {
				friend_loc = null;
			}
			if(targets.size() > 0) {
				target_loc = targets.peek();
			} else {
				target_loc = null;
			}
			
			// interrupt missions (only for PASTR and NOISETOWER)
			if(mission.ordinal() >= Order.PASTR.ordinal()) {
				current_channel = CONSTRUCT_CHANNEL;
				broadcast_mission(target, mission);
				mission = Order.ATTACK;
				target = enemy_loc.getLocation();
				broadcast_mission(target, Order.ATTACK);
			}
			
			
			// first check whether target is closer of enemies is closer
			if( enemy_loc == null || (target_loc != null && target_loc.getDist() < enemy_loc.getDist())) {
				// we know we are closest to an enemy pasture or noisetower
				if(target_loc.dist <= attackRadius) {
					if(my_health > 10 || baddies.size() == 0) {
						// attack target
						if(rcf.canAttackSquare(target_loc.getLocation())) {
							rcf.attackSquare(target_loc.getLocation());
						}
					} else {
						// get away from whoever is shooting you
						move_to(rcf.getLocation().directionTo(target_loc.getLocation()).opposite(), true, enemy_loc.getLocation());
					}
				} else {
					// move closer to target and attack?
					if(my_health > 10 || baddies.size() == 0) {
						move_to(rcf.getLocation().directionTo(target_loc.getLocation()), true, enemy_hq);
					} // else don't move
				}
				
			} else {
				// we know that an enemy soldier is the closest threat
				// good check to see if there is a wall in front of our guys, keep
				MapLocation wall_check = wall_ahead(rcf.getLocation(), enemy_loc.getLocation(), 10000);
				if(wall_check != null && wall_check.distanceSquaredTo(enemy_loc.getLocation()) > RobotType.SOLDIER.attackRadiusMaxSquared) return false;
				
				//check their distance
				if(enemy_loc.getDist() <= attackRadius) {
					// If they are in attack range:
					
					// what are the number of enemies in my attack range?
					int num_enemies =0;
					for(MapLocDist mld : baddies) {
						if(mld.getDist() <= attackRadius) {
							num_enemies++;
						}
					}
					
					// what are the number of friends in his attack range? 
					int num_friends = 0;
					for(MapLocDist mld : allies) {
						if(mld.getLocation().distanceSquaredTo(enemy_loc.getLocation()) < attackRadius) {
							num_friends++;
						}
					}
					
					// if the number of friends is less than enemies, retreat
					if(num_friends <= num_enemies) {
						move_to(rcf.getLocation().directionTo(enemy_loc.getLocation()).opposite(), false, enemy_hq);
					} else {
						// if we have more friends than enemies, and are in attack range:
						
						// If our health is above 1/4 of max:
						if(my_health > 25) {
							// shoot!
							if(rcf.canAttackSquare(enemy_loc.getLocation())) {
								rcf.attackSquare(enemy_loc.getLocation());
							}
						} else {
							// retreat
							move_to(rcf.getLocation().directionTo(enemy_loc.getLocation()).opposite(), false, enemy_hq);
						}
						
					}
					// this completes decision process if the enemy is in range
				} else {
					// they are not in attack range.
					
					// are they almost in attack range? 
					if(enemy_loc.getDist() <= attackRadius + 2*sqrt_attackRadius + 1)  {
						// stay put for now
						
						
					} else {
						// they are not close. 
						// for now, move to them
						if(friend_loc != null) {
						
							// if the friend is closer than you
							if(friend_loc.getLocation().distanceSquaredTo(enemy_loc.getLocation()) < enemy_loc.getDist()) {
								// move to their side
								move_to(friend_loc.getLocation().directionTo(enemy_loc.getLocation()).rotateLeft().rotateLeft(), false, enemy_hq);
							} else {
								// move to the enemy
								move_to(rcf.getLocation().directionTo(enemy_loc.getLocation()), false, enemy_hq);
							}
							
						} else {
							move_to(rcf.getLocation().directionTo(enemy_loc.getLocation()), false, enemy_hq);
						}
						
							
					
					}
					
				}
				// this completes the decision process for any distance
			}
			return true;
		} 
		return false;
	} */
	
	public static boolean battlecode() throws GameActionException {
		// attack enemies
		// battlecode!
		
		// free, get health
		my_health = rcf.getHealth();
		
		// 200 bytecodes, pretty necessary
		Robot[] enemies = rcf.senseNearbyGameObjects(Robot.class, RobotType.SOLDIER.sensorRadiusSquared, rcf.getTeam().opponent());
		Robot[] friends = rcf.senseNearbyGameObjects(Robot.class, RobotType.SOLDIER.sensorRadiusSquared, rcf.getTeam());
		
		MapLocation bad_hq_loc = null;
		MapLocation good_hq_loc = null;
		MapLocation closest_enemy = null;
		MapLocation temp_loc;
		int min_dist = 9999;
		int dist = 10000;
		int num_baddies = 0;
		int num_targets = 0;
		int num_defends = 0;
		int num_friends = 1; // 1 = you
		RobotType rob_type;
		for(Robot enemy: enemies) {
			rob_type = rcf.senseRobotInfo(enemy).type;
			if(rob_type == RobotType.SOLDIER) {
				num_baddies++;
				temp_loc = rcf.senseLocationOf(enemy);
				dist = rcf.getLocation().distanceSquaredTo(temp_loc);
				if(dist < min_dist) {
					min_dist = dist;
					closest_enemy = temp_loc;
				}
			}
			if(rob_type == RobotType.NOISETOWER) {
				num_targets++;
				temp_loc = rcf.senseLocationOf(enemy);
				dist = rcf.getLocation().distanceSquaredTo(temp_loc);
				if(dist < min_dist) {
					min_dist = dist;
					closest_enemy = temp_loc;
				}
			}
			if(rob_type == RobotType.PASTR) {
				num_targets++;
				temp_loc = rcf.senseLocationOf(enemy);
				dist = rcf.getLocation().distanceSquaredTo(temp_loc);
				if(dist < min_dist) {
					min_dist = dist;
					closest_enemy = temp_loc;
				}
			}
			if(rob_type == RobotType.HQ) bad_hq_loc = rcf.senseLocationOf(enemy);
		}
		for(Robot friend: friends) {
			rob_type = rcf.senseRobotInfo(friend).type;
			if(rob_type == RobotType.SOLDIER) num_friends++; 
			if(rob_type == RobotType.NOISETOWER) num_defends++;
			if(rob_type == RobotType.PASTR) num_defends++;
			if(rob_type == RobotType.HQ) good_hq_loc = rcf.senseLocationOf(friend);
		}

		if(num_baddies + num_targets > 0 || bad_hq_loc != null) {
			rcf.setIndicatorString(0, "Fighting enemies");
			MapLocation enemy_loc = null;
			if(closest_enemy == null) {
				enemy_loc = rcf.senseLocationOf(enemies[0]);
			} else {
				enemy_loc = closest_enemy;
			}
			rcf.setIndicatorString(2, "enemy_loc: " + enemy_loc + ", closest_enemy: " + enemies.length);
			// check if enemies are behind wall
			MapLocation wall_check = wall_ahead(rcf.getLocation(), enemy_loc, 10000);
			if(wall_check != null && wall_check.distanceSquaredTo(enemy_loc) > RobotType.SOLDIER.attackRadiusMaxSquared) return false;
			
			// interrupt missions (only for PASTR and NOISETOWER)
			if(mission.ordinal() >= Order.PASTR.ordinal()) {
				current_channel = CONSTRUCT_CHANNEL;
				broadcast_mission(target, mission);
				mission = Order.ATTACK;
				target = enemy_loc;
				broadcast_mission(target, Order.ATTACK);
			}
			
			if(rcf.canAttackSquare(enemy_loc)) {
				if(my_health > 40) {
					rcf.setIndicatorString(1, "Shooting enemies " + enemy_loc);
					rcf.attackSquare(enemy_loc);
				} else {
					if(wall_check == null && rcf.senseNearbyGameObjects(Robot.class, enemy_loc, 1, rcf.getTeam().opponent()).length >= 2) {
						// suicide bomb!
						rcf.setIndicatorString(1, "Suicide bomb" + enemy_loc);
						if(rcf.getLocation().distanceSquaredTo(enemy_loc) > 1) {
							move_to(rcf.getLocation().directionTo(enemy_loc), false, null);
						} else {
							rcf.selfDestruct();
						}
					} else {
						rcf.setIndicatorString(1, "Escape death " + enemy_loc);
						move_to(rcf.getLocation().directionTo(enemy_loc).opposite(), false, enemy_hq);
					}
				}
			}
			else {
				//if out-numbered run away
				Direction enemy_dir = rcf.getLocation().directionTo(enemy_loc);
				if(num_friends <= num_baddies || my_health < 25){
					rcf.setIndicatorString(1, "Conservative " + enemy_loc);
					move_to(enemy_dir.opposite(), false, enemy_hq);
				} else {
					//attack!
					rcf.setIndicatorString(1, "Aggressive " + enemy_loc);
					if(rcf.getLocation().add(enemy_dir).distanceSquaredTo(enemy_loc) > RobotType.SOLDIER.attackRadiusMaxSquared) {
						move_to(enemy_dir, false, enemy_hq);
					}
				}
			}
			return true;
		} 
		return false;
	}
	
	public static void runTower(RobotController rct) throws GameActionException {
		if(shoot_places == null) {
			nearby_pastr = find_closest_pasture(rct.getLocation());
			if(nearby_pastr == null || nearby_pastr.distanceSquaredTo(rct.getLocation()) > 9) return;
			shoot_places= new ArrayList<MapLocation>();
			for(Direction dir : allDirections) {
				MapLocation tip = nearby_pastr.add(dir);
				while(tip.add(dir).distanceSquaredTo(rcf.getLocation()) < RobotType.NOISETOWER.attackRadiusMaxSquared) {
					tip = tip.add(dir);
				}
				while(tip.subtract(dir).distanceSquaredTo(nearby_pastr) >= GameConstants.ATTACK_SCARE_RANGE) {
					shoot_places.add(tip);
					tip = tip.subtract(dir);
				}
			}
			shoot_iterator = shoot_places.iterator();
			return;
		}
		if(rcf.isActive() && rct.getActionDelay() == 0) {
			if(!shoot_iterator.hasNext()) {
				shoot_iterator = shoot_places.iterator();
			}
			MapLocation loc = shoot_iterator.next();
			if(rct.canAttackSquare(loc)) {
				rct.attackSquare(loc);
			}
	
		}
		
	}
	
	public static double pasture_potential(MapLocation m) {
		double milk_potential = 0;
		for(Direction dir : allDirections) {
			MapLocation temp_loc = m.add(dir, 3);
			while(m.distanceSquaredTo(temp_loc) < RobotType.NOISETOWER.attackRadiusMaxSquared) {
				if(rcf.senseTerrainTile(temp_loc) == TerrainTile.OFF_MAP || 
						rcf.senseTerrainTile(temp_loc) == TerrainTile.VOID) {
					break;
				}
				milk_potential += cow_growth[temp_loc.x][temp_loc.y];
				temp_loc = temp_loc.add(dir);
			}
			
		}
		
		return milk_potential;
	}
	
	public static MapLocation find_closest_pasture(MapLocation m) {
		int min_dist = 999999;
		MapLocation best_loc = null;
		for(MapLocation loc : rcf.sensePastrLocations(rcf.getTeam())) {
			int dist = m.distanceSquaredTo(loc);
			if(dist < min_dist) {
				min_dist = dist;
				best_loc = loc;
			}
		}
		return best_loc;
	}
	
	
	
	public static Direction bug_nav_next(MapLocation end) throws GameActionException {
		// are we currently on a path?
		Direction next = null;
		MapLocation current_loc = rcf.getLocation();		
		if(bug_path != null && !bug_path.isEmpty() && current_loc.equals(bug_path.get(0))) {
			bug_path.remove(0);
		}
		if(bug_path == null || bug_path.isEmpty()) {
			MapLocation wall_start = wall_ahead(current_loc, end, 1);
			if(wall_start == null) {
				return(current_loc.directionTo(end));
			} else {
				if(last_path ==null || last_path.isEmpty()) {
					bug_path = bug_wall_nav(wall_start, end, new ArrayList<MapLocation>());
					last_path = new ArrayList<MapLocation>(bug_path);
				} else {
					bug_path = bug_wall_nav(wall_start, end, last_path);
					last_path = new ArrayList<MapLocation>(bug_path);
				}
				//System.out.println("Bug Path: " + bug_path);
			}
		}
		next = current_loc.directionTo(bug_path.get(0));
		if(!walkable_tile(current_loc.add(next))){ 
			bug_path = null;
			last_path = null;
		} 
		rcf.setIndicatorString(2, "" + bug_path);
		return next;
	}
	
	public static MapLocation wall_ahead(MapLocation start, MapLocation end, int multiple) {
		int tried = 0;
		Direction dir = start.directionTo(end);
		while(tried < multiple) {
			if(start.equals(end)) {
				return null;
			}
			if(!walkable_tile(start.add(dir))) {
				return start;
			}
			start = start.add(dir);
			dir = start.directionTo(end);
			tried++;
		}
		return null;
	}
	
	
	// returns corners
	public static ArrayList<MapLocation> bug_wall_nav(MapLocation start, MapLocation end, ArrayList<MapLocation> last_path) {
		// assume at wall, we need to find directions to move in
		ArrayList<MapLocation> pathright = new ArrayList<MapLocation>();
		ArrayList<MapLocation> pathleft = new ArrayList<MapLocation>();
		Direction wall_dir = start.directionTo(end);
		Direction right_dir = null;
		Direction left_dir = null;
		boolean prefer_left = true;
		boolean prefer_right = true;
		// find the right and left "wall hugging" directions
		
		// remove bug that causes me to be sad 
		last_path.remove(start);
		
		while(right_dir == null) {
			if(walkable_tile(start.add(wall_dir.rotateRight())) && !wall_dir.rotateRight().isDiagonal()) {
				right_dir = wall_dir.rotateRight();
				break;
			}
			wall_dir = wall_dir.rotateRight();
		}
		while(left_dir == null) { 
			if(walkable_tile(start.add(wall_dir.rotateLeft())) && !wall_dir.rotateRight().isDiagonal()) {
				left_dir = wall_dir.rotateLeft();
				break;
			}
			wall_dir = wall_dir.rotateLeft();
		}
	
		int paths_evaluated = 0;
		MapLocation left_loc = start;
		MapLocation right_loc = start;
		while(paths_evaluated < PATHS_MAX || pathright.size() < 1) {
			Direction temp_dir = left_dir;
			// can I turn right into the right direction?
			do {
				if(temp_dir == left_loc.directionTo(end) && prefer_left && wall_ahead(left_loc, end, 1) == null){
					pathleft.add(left_loc);
					return pathleft;
				}
				temp_dir = temp_dir.rotateRight();
			} while(walkable_tile(left_loc.add(temp_dir)));
			
			temp_dir = right_dir;
			do {
				if(temp_dir == right_loc.directionTo(end) && prefer_right && wall_ahead(right_loc, end, 1) == null) {
					pathright.add(right_loc);
					return pathright;
				}
				temp_dir = temp_dir.rotateLeft();
			} while(walkable_tile(right_loc.add(temp_dir)));

			// LEFT PATH: 
			// if we are on a left path we are looking to turn right
			// if we can we are at a corner and will add this point to our
			// final path (tangential bug-nav)
			// don't turn when starting at a corner though
			if(walkable_tile(left_loc.add(left_dir.rotateRight().rotateRight())) && paths_evaluated != 0) {
				if(last_path.contains(left_loc)) {
					//System.out.println("Prefer not to rotate left");
					prefer_left = false;
				}
				pathleft.add(left_loc);
				left_dir = left_dir.rotateRight().rotateRight();
				left_loc = left_loc.add(left_dir);
			} else {
				if(walkable_tile(left_loc.add(left_dir))) {
					left_loc = left_loc.add(left_dir);
				} else {
					// in this case we must be in a cusp, so turn left until we
					// can walk again.
					do {
						left_dir = left_dir.rotateLeft().rotateLeft();
					} while(!walkable_tile(left_loc.add(left_dir)));
					left_loc = left_loc.add(left_dir);
				}
			}
			// RIGHT PATH:
			// if we are on a right path we are looking to turn left
			// if we can we are at a corner and will add this to our 
			// final path
			// don't turn when starting at a corner though
			if(walkable_tile(right_loc.add(right_dir.rotateLeft().rotateLeft())) && paths_evaluated != 0) { 
				if(last_path.contains(right_loc)) {
					//System.out.println("Prefer not to turn right");
					prefer_right = false;
				}
				pathright.add(right_loc);
				right_dir = right_dir.rotateLeft().rotateLeft();
				right_loc = right_loc.add(right_dir);
			} else {
				// try to keep going
				if(walkable_tile(right_loc.add(right_dir))){ 
					right_loc = right_loc.add(right_dir);
				} else {
					// we are in a cusp and must turn right
					do {
						right_dir = right_dir.rotateRight().rotateRight();
					} while(! walkable_tile(right_loc.add(right_dir)));
					right_loc = right_loc.add(right_dir);
				}
			}
			paths_evaluated++;
			//System.out.println("Left_loc: " + left_loc + ", right_loc: " + right_loc);
		}
		if(randy.nextDouble() > .5) {
			return pathright;
		} else return pathleft;
	}

	
	
	public static boolean walkable_tile(MapLocation m) {
		if(rcf.senseTerrainTile(m) == TerrainTile.NORMAL ||
				rcf.senseTerrainTile(m) == TerrainTile.ROAD) {
			return true;
		} else return false;
	}
	
	
	/*
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
	*/
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
	

	
	
	/*
	public static void update_explored() {
		// for all map locations in sight
		int sensor_radius_sq = RobotType.SOLDIER.sensorRadiusSquared;
		MapLocation[] locations = MapLocation.getAllMapLocationsWithinRadiusSq(rcf.getLocation(), (sensor_radius_sq));
		for(MapLocation loc : locations) {
			//System.out.println("loc: " + loc.x + " " + loc.y);
			if(rcf.senseTerrainTile(loc) != TerrainTile.OFF_MAP){ 
				explored[loc.x][loc.y] = true;
			}
		}
	}*/
	 
	public static ArrayList<MapLocation> find_fertile_ground() {
		fertile_ground = new ArrayList<MapLocation>(rcf.getMapHeight()*rcf.getMapWidth());
		double max_growth = -1;
		for(int x =xfert; x < rcf.getMapWidth(); x++) {
			for(int y = yfert; y < rcf.getMapHeight(); y++) {
				if(cow_growth[x][y] > max_growth) {
					max_growth = cow_growth[x][y];
					fertile_ground = new ArrayList<MapLocation>();
				}
				if(cow_growth[x][y] == max_growth) {
					fertile_ground.add(new MapLocation(x,y));
				}
				if(Clock.getBytecodesLeft() < 1000) {
					xfert = x;
					yfert = y + 1;
					return null;
				}
			}
		}
		return fertile_ground;
	}
	
	private class Plantation {
		MapLocation top;
		MapLocation bottom;
		MapLocation left;
		MapLocation right;
		
		public Plantation(MapLocation t, MapLocation b, MapLocation l, MapLocation r) {
			top = t;
			bottom = b;
			left = l;
			right = r;
		}
		
		public boolean contains(MapLocation m) {
			return (left.x < m.x &&
					m.x < right.x &&
					m.y < top.y &&
					bottom.y < m.y
					);
		}
		
		public boolean contains(Plantation p) {
			return (contains(p.top) ||
					contains(p.bottom) ||
					contains(p.left) ||
					contains(p.right)
					);
		}
		
		public void merge(Plantation p) {
			if(top.y < p.top.y) {
				top = p.top;
			}
			if(bottom.y > p.bottom.y){
				bottom = p.bottom;
			}
			if(right.x < p.right.x){
				right = p.right;
			}
			if(left.x > p.left.x){
				left = p.left;
			}
		}
		
		public MapLocation getCenter() {
			int avgx = right.x + left.x / 2;
			int avgy = top.y + bottom.y / 2;
			return new MapLocation(avgx, avgy);
		}
	}
	
	// this method does not merge areas, I don't think it will be needed
	// fuck fuck fuck I can't figure out how to do this.... 
	public static ArrayList<MapLocation> find_sweet_pasture_locations() {
		ArrayList<MapLocation> best_locations = find_fertile_ground();
		if(best_locations == null) return null;
		Comparator<MapLocation> compy = new Comparator<MapLocation>() {

			public int compare(MapLocation arg0, MapLocation arg1) {
				return arg0.distanceSquaredTo(hq_loc) - arg1.distanceSquaredTo(hq_loc);
			}
			
		};
		
		Collections.sort(best_locations, compy);
		return best_locations;
	}
	
	public static MapLocation find_average(Collection<MapLocation> coll) {
		int size = coll.size();
		int avgx = 0;
		int avgy = 0;
		for(MapLocation m : coll) {
			avgx += m.x;
			avgy += m.y;
		}
		avgx = avgx/size;
		avgy = avgy/size;
		
		return new MapLocation(avgx, avgy);
	}
	
	/*
	public static void navigate_controller(RobotController rcs, MapLocation loc) throws GameActionException {
		/* if(corners == null) {
		 
			download_corners();
		}
		
		// look for squares with high cow counts, establish pastures on
		// high cow counts and noise towers between them? 
		pastures = new ArrayList<MapLocation>();
		noisetower_num = 0;
		ArrayList<MapLocation> high_cows = sense_high_cows();
		if(pastures.size() >= 1 && construction_free(pastures.get(0), RobotType.NOISETOWER, 1)) {
			System.out.println("I'm looking to set up a tower!");
			tower_location = pastures.get(0).add(Direction.NORTH);
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
			// or else we go to the target location
			if(target == rcf.senseHQLocation()) {
				move_to(rcf.senseHQLocation());
				return;
			}
			if(path == null || path_iterator == null) {
				path = a_star("gototarget");
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
	*/
	
	
	public static void broadcast_mission(MapLocation m, Order order) throws GameActionException {
		boolean broadcasted = false;
		while(!broadcasted) {
			if(rcf.readBroadcast(current_channel) <= 0) {
				int message = order.ordinal()*100*100 + m.x*100 + m.y;
				rcf.broadcast(current_channel, order.ordinal()*100*100 + m.x*100 + m.y);
				System.out.println("Broadcasted: " + order + " at " + m + " to channel " + current_channel + ", " + message);
				broadcasted = true;
			}
			current_channel++;
		}
	}
	
	/*
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
	*/
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
			if(rcf.senseCowsAtLocation(loc) > 3000 && !in_range_of_pasture(loc, 4)) {
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
	
	public static boolean hq_fight() throws GameActionException {
		Robot[] enemies = rcf.senseNearbyGameObjects(Robot.class, RobotType.HQ.sensorRadiusSquared, rcf.getTeam().opponent());
		boolean shot = false;
		if(enemies.length > 0) {
			for(Robot enemy : enemies) {
				MapLocation enemy_loc = rcf.senseRobotInfo(enemy).location;
				if(rcf.canAttackSquare(enemy_loc)) {
					rcf.attackSquare(enemy_loc);
					shot = true;
					break;
				} else {
					if(rcf.canAttackSquare(enemy_loc.add(enemy_loc.directionTo(hq_loc)))) {
						rcf.attackSquare(enemy_loc.add(enemy_loc.directionTo(hq_loc)));
						shot = true;
						break;
					} else {
						rcf.setIndicatorString(2, "No I can't attack that");
					}
				}
			}
		}
		return shot;
	}
	
	public static boolean hq_spawn() throws GameActionException {
		for(Direction dir : allDirections) {
			if(rcf.canMove(dir)) {
				rcf.spawn(dir);
				// should create spawn delay 
				return true;
			}
		}
		return false;
	}
	
	public static void hq_signals() throws GameActionException {
		current_channel = CONSTRUCT_CHANNEL;
		// if we haven't checked the HQ viability
		if(rcf.readBroadcast(HQ_VIABILITY_CHANNEL) == 0) {
			// tell peeps to construct a tower at base and a pasture next to it
			// TODO check rush distance
			if(pasture_potential(hq_loc) > 5) {
				boolean success = false;
				hq_viable = true;
				/*
				for(Direction dir : allDirections) {
					if(rcf.canMove(dir) && rcf.canMove(dir.rotateRight())){ 
						rcf.broadcast(PASTR_COUNT_CHANNEL, rcf.readBroadcast(PASTR_COUNT_CHANNEL) + 1);
						broadcast_mission(hq_loc.add(dir), Order.NOISETOWER);
						broadcast_mission(hq_loc.add(dir.rotateRight()), Order.PASTR);
						success = true;
						break;
					}
				}
				*/
				if(success) rcf.broadcast(HQ_VIABILITY_CHANNEL, -1);
					else rcf.broadcast(HQ_VIABILITY_CHANNEL, -2);

			} else {
				rcf.broadcast(HQ_VIABILITY_CHANNEL, -2);
			}
			return;
		} else {
			MapLocation[] my_pastures = rcf.sensePastrLocations(rcf.getTeam());
			MapLocation[] enemy_pastures = rcf.sensePastrLocations(rcf.getTeam().opponent());
			int pastures_constructing = rcf.readBroadcast(PASTR_COUNT_CHANNEL);
			int disadvantage = enemy_pastures.length + 1 - (my_pastures.length + pastures_constructing);
			
			/*
			if(!attacked && my_pastures.length > 0) {
				broadcast_mission(enemy_hq, Order.GOTO);
				broadcast_mission(enemy_hq, Order.GOTO);
				broadcast_mission(enemy_hq, Order.GOTO);
				attacked = true;
			}*/
			
			// make sure our pasture count is always greater than theirs
			if(disadvantage > 0) {
				// if they are 2 ahead they must not be guarding one of their towers
				if((Clock.getRoundNum()/100) > maxclock) {
					rcf.setIndicatorString(1, "I've seen the light");
					for(MapLocation m : enemy_pastures) {
						broadcast_mission(m, Order.ATTACK);
						maxclock = Clock.getRoundNum()/100; 
					}
				}
				
				if(sweet_pasture_locations == null) {
					sweet_pasture_locations = find_sweet_pasture_locations();		
				} else {
					if(pasture_index < sweet_pasture_locations.size() && Clock.getRoundNum() > 1200) {
						MapLocation center_loc = sweet_pasture_locations.get(pasture_index);
						rcf.broadcast(PASTR_COUNT_CHANNEL, rcf.readBroadcast(PASTR_COUNT_CHANNEL)+ 1);
						broadcast_mission(center_loc, Order.NOISETOWER);
						broadcast_mission(center_loc.add(Direction.NORTH), Order.PASTR);
						for(int i=0; i<10; i++) {
							broadcast_mission(center_loc.add(Direction.SOUTH), Order.DEFEND);
						}
						pasture_index += 50;
					}
				}
			}
			
				
			
		}
	}
	
	// spawn Robots around HQ
	public static void runHQ(RobotController rch) throws GameActionException {
		if(rch.isActive()) {
			
			// schedule orders 

			// fight baddies
			if(hq_fight()) return;
			
			hq_signals();
			
			// spawn
			if(rch.senseRobotCount() < GameConstants.MAX_ROBOTS && hq_spawn()) return;
			
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
	
	/*
	// very raw a*
	// such beautiful code! Such wonderful side effects! 
	public static ArrayList<Direction> a_star(String goal) throws GameActionException {
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
				if(current.current_loc.equals(target) || current.path.size() >= ASTAR_LIMIT) {
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
	*/
	/*
	private static class Pather {
		
		
		public 
	}*/
}
