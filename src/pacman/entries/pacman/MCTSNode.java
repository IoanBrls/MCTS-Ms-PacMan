package pacman.entries.pacman;

import java.util.ArrayList;

import pacman.game.Game;
import pacman.game.Constants.MOVE;

public class MCTSNode {
	
	public Game game;
	public int nodeIndex;
	public ArrayList<MCTSNode> children = new ArrayList<MCTSNode>();
	public MCTSNode parent;
	public MOVE parentMove;
	public float reward;
	public int timesvisited;
	
	public MCTSNode(Game game){
		this.game = game.copy();
		nodeIndex = game.getPacmanCurrentNodeIndex();
		timesvisited = 0;
		reward = 0;
	}
	
	public MCTSNode() {
		timesvisited = 0;
		reward = 0;
		parentMove = MOVE.NEUTRAL;
		parent = null;
	}
	
	public Game getGame() {
		return game.copy();
	}
}
