package waffleoRai_myuctempl;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class AsmScanning {
	
	public static final int ARGTYPE_UNK = -1;
	public static final int ARGTYPE_REGISTER = 0;
	public static final int ARGTYPE_LITERAL = 1;
	public static final int ARGTYPE_SYMBOL = 2;
	public static final int ARGTYPE_HALF_SYMBOL = 3; //eg. %hi(mySymbol) when lui/addiu loading addresses
	
	public static class AsmLine{
		public String cmd;
		public List<String> args;
		public boolean colonEnd = false;
		
		public CommentedLine rawLine;
	}
	
	public static class CommentedLine{
		public String line;
		public List<String> comments;
		
		public CommentedLine() {
			comments = new LinkedList<String>();
		}
	}
	
	public static CommentedLine separateComments(String line) {
		CommentedLine cl = new CommentedLine();
		
		int cmStart = line.indexOf("/*");
		int cmEnd = line.indexOf("*/");
		while((cmStart >= 0) && (cmEnd >= 0)) {
			String before = line.substring(0, cmStart);
			String after = line.substring(cmEnd + 2);
			String comment = line.substring(cmStart+2, cmEnd).trim();
			line = before + after;
			line = line.trim();
			cl.comments.add(comment);
			
			cmStart = line.indexOf("/*");
			cmEnd = line.indexOf("*/");
		}
		cl.line = line;
		
		return cl;
	}
	
	public static int determineArgType(String arg) {
		if(arg == null) return ARGTYPE_UNK;
		if(arg.startsWith("$")) return ARGTYPE_REGISTER;
		if(arg.startsWith("%")) return ARGTYPE_HALF_SYMBOL;
		if(arg.startsWith("0x")) return ARGTYPE_LITERAL;
		if(arg.startsWith("-0x")) return ARGTYPE_LITERAL;
		
		return ARGTYPE_SYMBOL;
	}
	
	public static AsmLine parseLine(String line) {
		if(line == null) return null;
		line = line.trim();
		AsmLine aline = new AsmLine();
		
		//Remove comments
		aline.rawLine = separateComments(line);
		if(aline.rawLine.line == null) return null;
		
		//Check if ends with colon (then is likely local label)
		line = aline.rawLine.line;
		if(line.endsWith(":")) {
			aline.colonEnd = true;
			line = line.substring(0, line.length()-1);
			line = line.trim();
		}
		
		//Extract command
		int wsIndex = line.indexOf(' ');
		if(wsIndex < 0) wsIndex = line.indexOf('\t');
		if(wsIndex >= 0) {
			aline.cmd = line.substring(0, wsIndex);
			line = line.substring(wsIndex+1);
			line = line.trim();
		}
		else aline.cmd = line;
		
		//Split arguments, first by commas, then by parentheses
		if(!line.isEmpty()) {
			//Still something left after removed command
			
			//clean up
			line = line.replace(" ", "");
			line = line.replace("\t", ""); 
			
			String[] parts = line.split(",");
			aline.args = new ArrayList<String>(8);
			for(int i = 0; i < parts.length; i++) {
				if(parts[i].contains("(")) {
					String[] spl = parts[i].split("\\(");
					for(int j = 0; j < spl.length; j++) {
						if(spl[j].contains(")")) {
							String[] spl2 = spl[j].split("\\)");
							for(int k = 0; k < spl2.length; k++) {
								aline.args.add(spl2[k]);
							}
						}
						else aline.args.add(spl[j]);
					}
				}
				else aline.args.add(parts[i]);
				
			}
		}

		return aline;
	}
		
	public static int readInSymbols(String path, MCTModule dst) throws IOException {
		if(dst == null) return 0;
		int symCount = 0;
		int secType = MCTSymbol.SYMBOL_TYPE_TEXT;
		MCTSymbol currentSymbol = null;
		BufferedReader br = new BufferedReader(new FileReader(path));
		String line = null;
		while((line = br.readLine()) != null) {
			if(line.isEmpty()) continue;
			line = line.trim();
			if(line.startsWith(".section")) {
				//Note section type
				String line2 = line.replace(".section", "").trim();
				if(line2.startsWith(".text")) {
					secType = MCTSymbol.SYMBOL_TYPE_TEXT;
				}
				else if(line2.startsWith(".rodata")) {
					secType = MCTSymbol.SYMBOL_TYPE_RODATA;
				}
				else if(line2.startsWith(".data")) {
					secType = MCTSymbol.SYMBOL_TYPE_DATA;
				}
			}
			else if(line.startsWith("glabel") || line.startsWith("dlabel")) {
				if(currentSymbol != null) dst.addSymbol(currentSymbol);
				
				//New symbol
				currentSymbol = new MCTSymbol();
				currentSymbol.section = secType;
				String[] spl = line.split(" ");
				currentSymbol.name = spl[1];
				currentSymbol.asmLines.add(line);
				symCount++;
			}
			else {
				//Scan for symbols and add to current label, if available. 
				//If no current label, just ignore.
				if(currentSymbol != null) {
					currentSymbol.asmLines.add(line);
					AsmLine aline = parseLine(line);
					if(aline == null) continue;
					
					if(aline.colonEnd) {
						currentSymbol.blabels.add(aline.cmd);
					}
					else {
						if(aline.cmd.equals("jlabel")) currentSymbol.jlabels.add(aline.args.get(0));
						else {
							if(aline.cmd.equals(".size")) continue;
							
							//Check for address if not already noted
							if(currentSymbol.address == 0L) {
								for(String comment : aline.rawLine.comments) {
									String[] cspl = comment.split(" ");
									for(String s : cspl) {
										if(s.startsWith("80")) {
											try {
												currentSymbol.address = Long.parseUnsignedLong(s, 16);
											}
											catch(NumberFormatException ex) {}
										}
									}
									if(currentSymbol.address != 0L) break;
								}
							}
							
							if(aline.args != null) {
								for(String arg : aline.args) {
									int atype = determineArgType(arg);
									if(atype == ARGTYPE_SYMBOL) {
										currentSymbol.dependencies.add(arg);
									}
								}
							}
						}
					}
				}
			}
		}
		br.close();
		
		if(currentSymbol != null) dst.addSymbol(currentSymbol);
		dst.linkLocalROData();
		
		return symCount;
	}
	
	public static Set<String> quickGetSymbols(String path) throws IOException {
		//Do a quicker scan for just the glabels and dlabels in a file.
		Set<String> symbols = new HashSet<String>();
		BufferedReader br = new BufferedReader(new FileReader(path));
		String line = null;
		while((line = br.readLine()) != null) {
			if(line.isEmpty()) continue;
			AsmLine aline = parseLine(line);
			if(aline == null) continue;
			if(aline.cmd.equals("glabel") || aline.cmd.equals("dlabel")) {
				symbols.add(aline.args.get(0));
			}
		}
		br.close();
		return symbols;
	}
	
	public static void scanForSymbols(String path, MCTModule src) throws IOException {
		//Scans target for symbols in src and marks those found as public
		//I'm gonna do this a clumsy stupid way because I am so very lazy.
		List<String> fileLines = new LinkedList<String>();
		BufferedReader br = new BufferedReader(new FileReader(path));
		String line = null;
		while((line = br.readLine()) != null) {
			line = line.trim();
			if(line.isEmpty()) continue;
			//Remove comments
			CommentedLine cl = separateComments(line);
			line = cl.line;
			if(line == null || line.isEmpty()) continue;
			fileLines.add(line);
		}
		br.close();
		
		//Now just string match in memory lol
		for(MCTSymbol sym : src.rodataSymbols) {
			if(sym.isPublic) continue;
			for(String l : fileLines) {
				if(l.contains(sym.name)) {
					sym.isPublic = true;
					break;
				}
			}
		}
		
		for(MCTSymbol sym : src.dataSymbols) {
			if(sym.isPublic) continue;
			for(String l : fileLines) {
				if(l.contains(sym.name)) {
					sym.isPublic = true;
					break;
				}
			}
		}
		
		for(MCTSymbol sym : src.textSymbols) {
			if(sym.isPublic) continue;
			for(String l : fileLines) {
				if(l.contains(sym.name)) {
					sym.isPublic = true;
					break;
				}
			}
		}
	}

}
