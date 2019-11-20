package pacman.entries.pacman;

import pacman.game.Game;
import pacman.game.Constants.*;
import pacman.controllers.Controller;
import pacman.controllers.examples.*;

import java.util.Random;
import java.util.ArrayList;
import java.util.EnumMap;

//import pacman.game.internal.Node;

public class MCTSController extends Controller<MOVE> {
	
	private Controller<EnumMap<GHOST,MOVE>> ghostsSimulator = new Legacy2TheReckoning();
	
	private final Random random = new Random();
	
	private int maxIterations = 100;
	private int maxRolloutDepth = 10000;
	private float C = (float)(1.0/Math.sqrt(2));
	
	//rootNode is the starting Node of the MCTS Tree and it is always the current state of the game.
	private MCTSNode rootNode;
	//currentNode is the Node that the MCTS algorithm examines at that point.
	private MCTSNode currentNode;
	
	
	public MOVE getMove(Game game, long timedue) {
		
			rootNode = new MCTSNode(game.copy());
			int iterations = 0;
			float reward = 0;
			MOVE bestAction = MOVE.NEUTRAL;


			//The main loop of MCTS.
			while(iterations < maxIterations) {
				int lives = game.getPacmanNumberOfLivesRemaining();
				iterations++;
				TreePolicy();
				reward = DefaultPolicy(currentNode.getGame(),lives);
				Backpropagate(reward);
				EvaluateRewards();
			}
			
			currentNode = rootNode;
			BestChild(C);
			
			bestAction = currentNode.parentMove;
			//System.out.println(currentNode.reward);
			return bestAction;
	}
	
	//The Tree Policy. 
	private void TreePolicy() {

		currentNode = rootNode;
		
		int level = currentNode.getGame().getCurrentLevel();
		
		//The terminate conditions.
		while(!currentNode.getGame().gameOver() && currentNode.getGame().getCurrentLevel() == level) {
			
			if(!FullyExpanded(currentNode)) {
				Expand();
				return;
			}
			else {
				BestChild(C);
			}
			
		}
	}
	
	//The default policy. Basically a simulation of the game until we reach a terminal state.
	private float DefaultPolicy(Game game, int lives) {
		
		int level = game.getCurrentLevel();
		int count = 0;
		int pacmanIndex = currentNode.nodeIndex;
		int currentlives = game.getPacmanNumberOfLivesRemaining();
		float score = 0;
		
		//negative reward if you die. This is basically the the condition that
		//prevents PacMan from running into the ghosts.
		if (currentlives < lives)
			score -=25000;
		
		/*Rollout until you reach a node where the game has ended (win/loss) 
		* or until you visited enough nodes. Till simulate the game with random
		* pacman actions and use the Legacy2Reckoning ghost controller to simulate
		* ghosts movement.*/
		
		while ( !game.gameOver() && game.getCurrentLevel() == level 
				&& count < maxRolloutDepth) {
			
			count++;
			MOVE action = RandomAction(game, pacmanIndex);
			game.advanceGame(action, ghostsSimulator.getMove(game, 0));
			pacmanIndex = game.getPacmanCurrentNodeIndex();
		}
		
		//Use the score achieved during the simulation as a reward for MCTS.
		return score + game.getScore();
		
	}
	
	//Backpropagate the reward to every parent node starting from the current one.
	private void Backpropagate(float reward) {
		while(currentNode != null) {
			currentNode.reward += reward;
			currentNode.timesvisited++;
			currentNode = currentNode.parent;
		}
	}
	
	/* The Reward Function.
	 * Evaluates the rewards of every child node of the rootNode. Basically,
	 * it checks which move leads to the closest edible ghost and which 
	 * move checks to the closest pill and if any of the children have that move
	 * they gain an extra bonus to their reward. Priority is given to eating the ghosts. 
	 */
	private void EvaluateRewards() {
		
		ArrayList<MCTSNode> children = rootNode.children;
		
		if(children.size() == 0)
			return;
		
		Game evaluatedGame = rootNode.getGame();
		MOVE moveTowardsGhost;
		MOVE moveTowardsPill;
		int currentIndex = evaluatedGame.getPacmanCurrentNodeIndex();
		int min = Integer.MAX_VALUE;
		int minEdibleGhostIndex = -1;
		int distance;
		int ghostIndex;
		
		//find the move that leads to the closest edible ghost
		for (GHOST ghost: GHOST.values()) {

            if (evaluatedGame.getGhostEdibleTime(ghost) > 0 ) {
                ghostIndex = evaluatedGame.getGhostCurrentNodeIndex(ghost);
                distance = evaluatedGame.getShortestPathDistance(currentIndex, ghostIndex);

                if (distance < min) {
                    min = distance;
                    minEdibleGhostIndex = ghostIndex;
                }
            }
        }
		
		//if there are edible ghosts on the map get the move that leads to the closest one.
        if (minEdibleGhostIndex > -1) {
        	moveTowardsGhost = evaluatedGame.getNextMoveTowardsTarget(currentIndex, minEdibleGhostIndex,DM.PATH);
        }
        else {
        	moveTowardsGhost = MOVE.NEUTRAL;
        }
        
        //find the move that leads to the closest pill
        int[] pills = evaluatedGame.getActivePillsIndices();
        int closestPillIndex = evaluatedGame.getClosestNodeIndexFromNodeIndex(currentIndex, pills, DM.PATH);

        moveTowardsPill = evaluatedGame.getNextMoveTowardsTarget(currentIndex, closestPillIndex, DM.PATH);
		
        //increase the reward if the move that MCTS chose was either one that leads to
        //an edible ghost or a pill or one that moves away from the ghosts.
		for (MCTSNode node:children) {
			if (node.parentMove == moveTowardsGhost)
				node.reward += 2500;
			
			if (node.parentMove == moveTowardsPill)
				node.reward += 200;
		}
	}
	
	//Expands a not fully expanded Node and creates a new child for it.
	private void Expand(){
		MOVE action  = UntriedAction(currentNode.getGame(), currentNode);
		Game tempGame = currentNode.getGame();
		tempGame.advanceGame(action, ghostsSimulator.getMove(tempGame, 0));
		
		MCTSNode child = new MCTSNode(tempGame.copy());
		
		currentNode.children.add(child);
		child.parentMove = action;
		child.parent = currentNode;
		currentNode = child;
		
	}
	
	
	//Chooses the best child of the current Node based on the UCT value.
	//After this the currentNode variable points to the best child.
	private void BestChild(float c) {
		MCTSNode nt = currentNode;
		MCTSNode bestChild = null;
		
		float bestValue = Float.NEGATIVE_INFINITY;
		for (MCTSNode child: nt.children) {
			float thisValue = UCTValue(child,c);
			if (thisValue > bestValue) {
				bestChild = child;
				bestValue = thisValue;
			}
		}
		
		currentNode = bestChild;
	}
	
	
	//The function that calculates the UCT value.
	private float UCTValue(MCTSNode n, float c) {
		float exploitation = n.reward/n.timesvisited;
		float exploration = (float)(2*c*Math.sqrt(2*Math.log(n.parent.timesvisited/n.timesvisited)));
		
		if (n.timesvisited > 0)
			return exploitation + exploration;
		else
			return 1;
	}
	
	/* Function that returns an action that has not yet been tried on this Node
	 * aka state of the game. Basically, an untried action is an action that is
	 * not associated with any of this Node's children. If all possible actions 
	 * have already been tried the function returns the neutral move.
	 */
	private MOVE UntriedAction(Game game, MCTSNode n) {
		MOVE[] possibleMoves = game.getPossibleMoves(n.nodeIndex);
		
		outer:
		for (int i=0;i<possibleMoves.length;i++) {
			for (int k=0;k < n.children.size();k++) {
				if(n.children.get(k).parentMove == possibleMoves[i]) {
					continue outer;
				}
			}
			if (i<possibleMoves.length)
				return possibleMoves[i];
		}
		return MOVE.NEUTRAL;
	}
	
	private boolean FullyExpanded(MCTSNode node) {
		
		if(node == null)
			System.out.println("NO GAME!!!");
		
		if(UntriedAction(node.getGame(), node) == MOVE.NEUTRAL)
			return true;
		else
			return false;
		
	}
	
	//Chooses a random action out of the possible actions in the current state of the game.
	private MOVE RandomAction(Game game, int index) {
		MOVE[] possibleMoves = game.getPossibleMoves(index);
		
		int moveIndex = random.nextInt(possibleMoves.length);
		
		
		return possibleMoves[moveIndex];
		
	}
	
}

