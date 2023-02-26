package waffleoRai_extractMyu;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class LiteNode {

	public String name;
	public String value;
	public Map<String, String> attr;
	
	public LiteNode parent;
	public List<LiteNode> children;
	
	public LiteNode(){
		attr = new HashMap<String, String>();
		children = new LinkedList<LiteNode>();
	}
	
	public LiteNode newChild(String child_node_name){
		LiteNode child = new LiteNode();
		children.add(child);
		child.parent = this;
		child.name = child_node_name;
		return child;
	}
	
}
