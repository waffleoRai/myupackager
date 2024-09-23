package waffleoRai_extractMyu.mains;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Utils.FileBuffer;
import waffleoRai_extractMyu.MyuArcCommon;
import waffleoRai_extractMyu.MyuPackagerLogger;

public class CtxGen {
	
	//TODO We do need to remove all the ifdefs cplusplus and whatnot
	//TODO Losing some lines in the GPUMacro file which is making it unparsable...
	//TODO Still getting some double additions for some reason??
	
	private static class DepNode{
		public String name; //Including any slashes
		public String path;
		
		public List<DepNode> deps;
		
		public DepNode prev;
		public DepNode next;
		public boolean checked = false;
		
		public DepNode() {
			deps = new LinkedList<DepNode>();
		}
	}
	
	private static class IfMode{
		public boolean writeIf = false;
		public boolean writeContents = false;
		
		public IfMode(boolean outer, boolean inner) {writeIf = outer; writeContents = inner;}
	}
	
	private static DepNode moveToBack(DepNode node, DepNode tail) {
		if(node == tail) return node;
		if(node.prev != null) {
			node.prev.next = node.next;
		}
		if(node.next != null) {
			node.next.prev = node.prev;
		}
		
		if(tail != null) {
			tail.next = node;
			node.prev = tail;
		}
		else node.prev = null;
		node.next = null;
		
		return node;
	}
	
	private static void scanFile(DepNode node, Map<String, DepNode> depmap, List<String> inclDirs) throws IOException {
		//Just look for the #include lines in the file to link depedencies
		//Scan
		BufferedReader br = new BufferedReader(new FileReader(node.path));
		String line = null;
		while((line = br.readLine()) != null) {
			if(line.startsWith("#include")){
				int q1 = line.indexOf('\"');
				if(q1 < 0) continue;
				int q2 = line.lastIndexOf('\"');
				if(q2 <= q1) continue;
				String inclName = line.substring(q1+1, q2);
				
				//Check if already found
				DepNode child = depmap.get(inclName);
				if(child != null) {
					node.deps.add(child);
					continue;
				}
				
				//Try to match it to a path
				String inclPath = null;
				for(String dir : inclDirs) {
					String trypath = MyuArcCommon.unixRelPath2Local(dir, inclName);
					if(FileBuffer.fileExists(trypath)) {
						inclPath = trypath;
						break;
					}
				}
				if(inclPath != null) {
					child = new DepNode();
					child.name = inclName;
					child.path = inclPath;
					node.deps.add(child);
					depmap.put(child.name, child);
				}
				else {
					MyuPackagerLogger.logMessage("CtxGen.scanFile", "WARNING: Include file \"" + inclName + "\" could not be found!");
				}
			}
		}
		br.close();
		
		//Do node children
		for(DepNode child : node.deps) {
			scanFile(child, depmap, inclDirs);
		}

	}
	
	private static String removeCommentsFromLine(String input) {
		String line = input;
		if(line.contains("//")) {
			//Remove everything after
			line = line.substring(0, line.indexOf("//"));
		}
		
		//TODO Multiline comments. Eventually. (Will need inside comment flag...)

		return line;
	}
	
	public static void main_ctxgen(Map<String, String> argmap) throws IOException {
		String target = argmap.get("target");
		String inclDirs = argmap.get("incl");
		String outpath = argmap.get("out");
		
		//TODO Arg check
		
		//Convert to absolute paths using Path for incldirs to make life easier
		String[] idirs = inclDirs.split(";");
		List<String> incllist = new ArrayList<String>(idirs.length);
		for(int i = 0; i < idirs.length; i++) {
			Path p = Paths.get(idirs[i]);
			String pstr = p.toAbsolutePath().toString();
			incllist.add(pstr);
		}
		
		//Read nodes
		DepNode root = new DepNode();
		root.path = target;
		Map<String, DepNode> depmap = new HashMap<String, DepNode>();
		depmap.put(".TARGET", root);
		scanFile(root, depmap, incllist);
		
		//Return if no dependencies...
		if(root.deps.isEmpty()) {
			MyuPackagerLogger.logMessage("CtxGen.main_ctxgen", "Target has no included dependencies! Nothing to print.");
			return;
		}
		
		//determine order and combine into one output file
		DepNode tail = root;
		LinkedList<DepNode> checkQueue = new LinkedList<DepNode>();
		checkQueue.add(root);
		while(!checkQueue.isEmpty()) {
			DepNode node = checkQueue.pop();
			if(node.checked) continue;
			node.checked = true;
			for(DepNode dep : node.deps) {
				if((dep.prev == null) && (dep.next == null)) checkQueue.add(dep);
				tail = moveToBack(dep, tail);
			}
		}
		
		//Decapitate
		if(root.next != null) {
			//Should not happen, but here just in case.
			root.next.prev = null;
			if(root == tail) tail = null;
		}
		
		//Debug, print queue.
		DepNode node = tail;
		MyuPackagerLogger.logMessage("CtxGen.main_ctxgen", "Queue:");
		while(node != null) {
			MyuPackagerLogger.logMessage("CtxGen.main_ctxgen", "\t" + node.name);
			node = node.prev;
		}
		
		BufferedWriter bw = new BufferedWriter(new FileWriter(outpath));
		while(tail != null) {
			BufferedReader br = new BufferedReader(new FileReader(tail.path));
			String line = null;
			String ifndef = null;
			IfMode imode = new IfMode(true, true);
			LinkedList<IfMode> ppifStack = new LinkedList<IfMode>();
			while((line = br.readLine()) != null) {
				line = removeCommentsFromLine(line);
				//Copy all lines that aren't #includes, comments or the header duplicate check macros
				if(line.isEmpty()) continue;
				if(line.startsWith("#include")) continue;
				if(line.startsWith("#if")) {
					if(line.startsWith("#ifndef")) {
						if(ifndef == null) {
							ifndef = line.replace("#ifndef", "").trim();
							ppifStack.push(imode);
							imode = new IfMode(false, true);
						}
						else {
							ppifStack.push(imode);
							imode = new IfMode(false, false);
						}
					}
					else if(line.startsWith("#ifdef")) {
						ppifStack.push(imode);
						imode = new IfMode(false, false);
					}
					else if(line.startsWith("#if 0")) {
						ppifStack.push(imode);
						imode = new IfMode(false, false);
					}
					else {
						ppifStack.push(imode);
						imode = new IfMode(true, true);
						bw.write(line + "\n");
					}
				}
				else if(line.startsWith("#define")) {
					if(ifndef == null) {
						if(imode.writeContents) bw.write(line + "\n");
					}
					else {
						String[] spl = line.split(" ");
						if(spl.length < 2) {
							continue;
						}
						spl[1] = spl[1].trim();
						if(!spl[1].equals(ifndef)) {
							if(imode.writeContents) bw.write(line + "\n");
						}
					}
				}
				else if(line.startsWith("#endif")) {
					if(imode.writeIf) bw.write(line + "\n");
					if(!ppifStack.isEmpty()) {
						imode = ppifStack.pop();
					}
				}
				else {
					if(imode.writeContents) bw.write(line + "\n");
				}
			}
			br.close();
			tail = tail.prev;
		}
		bw.close();
		
	}

}
