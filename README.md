Battlecode2014 Strategy Report / Postmortem
==============

by Darkpurple (Michael Flynn)

Cower in fear and bring a spoon, because DarkPurple Evil Dairy Conglomerate is here to fill the galaxy with raspberry flavor! Not really though, because I was eliminated in the 3rd round. Oh well, the fact that I was eliminated even after a month of hard work attests to the prestige of the tournament and the hard work of my competitors, and there is always next year. This document is an overview of my strategy and review of what I could have done better.

Strategy Components:
- Pathing
- Battle Micromanagement
- Macro

Pathing
---------
- "look-ahead" bug pathing assisted by uniform cost search

The goal of the pathing system was to guide robots to a rally point broadcasted by the HQ. It needed to be able to quickly navigate around maze-like obstacles while being computationally lightweight, as the 10,000 bytecode limit made standard solutions like a*-search infeasible. To solve this, a heirarchy of pathing methods was used. By default, the robot would try to move in the direction closer to the rallypoint, however this direction would not always lead to passable terrain. Luckily the entire map is accessible to each robot, so in the case where the path is blocked, the robot will compute 2 paths around its obstacle, 1 path where it turns left, the other where it turns right and saves each location where the bot turns around a corner (not saving cusps). Whichever path leads to an unobstructed path to the rallypoint first is saved and the robot moves towards the turning points in sequence, skipping any cusps along the way. The 2 main advantages of this pathing algorithm is that it is efficient and reliably finishes using much less than 10,000 bytecodes and that it avoids the pitfalls of plain bug-pathing. This algorithm is not perfect, however, and if the map is designed particularly nefariously it can be caught in a loop and it sometimes misses "crack-in-the-wall" type holes such as the one in the "house" map. To remedy this, I built another layer on top of the bug pathing, in which the HQ broadcasts the results of a uniform-cost-search from the rallypoint to a block of channels reserved for that purpose. If the cowboy detects that a UCS distance has been broacasted in his area, he will follow those distances to the rally point rather than use bug-pathing.

Battle Micro
-------------
- avoid being outnumbered
- herd
- dodge self-destructs
- do not step into enemy attack range unless:
    - there is a friend who will also step into range, and
    - doing so will put you and you friend in range of exactly 1 enemy bot, OR
    - a friend has already stepped up and you must step up to join him
- focus fire
- back up if you are low health or outnumbered and in enemy attack range


Each of these were implemented by checking the distance to the enemy and sorting the actions to take from there. If a robot was within attack range it would check if a bot was too close and could self-destruct it, if it was being shot at by more bots than it had friends, and if so back up. If not the bot would shoot and attempt to focus fire by reading a set of FIRING_CHANNELS and finding nearby locations to fire at, if it could not find any nearby focus points it would fire and broadcast it's own. If it was within one step of the enemy it would find a buddy and do a 1 turn simulation and ask if they were able to focus down 1 enemy, and if so, move forward. If it was far away but saw the enemy, it would move towards friends and towards/away from the enemy depending on whether we outnumbered them or they outnumbered us. 

Macro Strategy
---------------
- quickly aquire milk by establishing pastures that quickly pay for themselves
- attack enemy pastures
- if viable, use HQ as a mining point
- rebuild destroyed buildings

The HQ would command the troops by broacasting rally points and missions. If a robot picked up a mission it would follow that mission or else go to the rally point. Missions include attacking an area, defending an area, or going to an area while avoiding enemies. To open, my bots would be on this GOTO command to the highest milk growth spot. Once past a hardcoded time-limit, my bots would build a Noisetower that herded cows in, and then a pasture. They would then wait and defend against the enemy, unless the enemy builds a pasture. If the enemy builds a pasture, the hq continually sets up new pastures to keep our pasture count at 1 higher than the enemy. Buildings are rebuild after they are destroyed if possible by the HQ as it checks each pasture to make sure it is still there, and checks that Noisetowers have checked in using special Noisetower check-in channels.  

Bugs and Improvements
---------------------

- Robots treating pastures like enemy soldiers

I noticed this bug early on and I though I fixed it, however it seems like last minute changes to the code reintroduced this bug. Watching my robots not destroy Pusheen's pasture as it gathered milk to go on to win was a particularly tear-jerking moment.

- Robots advancing when they shouldn't, retreating when they shouldn't

There might have been an error in my implementation of my micro strategy, because sometimes my robots would move towards an enemy, only to get shot at and retreat, only to move back into range again. I might have introduced this bug with my last minute changes as well.

- Being victimized by Paddlegoat's advance

For some reason Paddlegoats (and he alone) was able to compute a method to advance his troops 1 by 1 so as to make my robots retreat every time. In this way he was able to herd my bots back to my hq, basically completely neutralizing me. I tried to get around this by making the GOTO mission to avoid enemy bots, but it did not work. 

- GOTO bots get annhilated if caught and trapped

Another victim of last minute changes, this happened in the qualifying tournament: out of the gate at the beginning of the match my bots will GOTO the rallypoint so as to get there qucikly and run into enemy troops. Because they have been told to avoid them, they do not shoot or attack, so they just run away. This doesn't always work and my bots often get destroyed if caught in the beginning.

- Bots will move to attack pasture near an HQ, but won't get close enough to avoid HQ splash damage

Speaks for itself, my bots detected a pasture built near the enemy's HQ, but wouldn't destroy it because their attack range would require them to get within the HQ's splash damage range.



Conclusion
-------------

I think my bot was the weakest in the Macro-game, which I spent relatively less time on. This ended up costing me. I think if I didn't have the GOTO move planned in the begginning, I would have faired better in the later rounds. Small changes make the difference. GG to my oponents. I look forward to competing again next year.
