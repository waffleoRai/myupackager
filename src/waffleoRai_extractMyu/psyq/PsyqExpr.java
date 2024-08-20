package waffleoRai_extractMyu.psyq;

import java.io.IOException;
import java.io.Writer;

import waffleoRai_Utils.BufferReference;

public class PsyqExpr {
	
	private int command;
	
	private int value;
	private int symbolId;
	private int sectionId;
	
	private PsyqExpr right;
	private PsyqExpr left;
	
	public static PsyqExpr parse(BufferReference input) {
		if(input == null) return null;
		PsyqExpr expr = new PsyqExpr();
		expr.command = Byte.toUnsignedInt(input.nextByte());
		
		switch(expr.command) {
		case PsyqObjCmd.EXPR_VALUE:
			expr.value = input.nextInt();
			break;
		case PsyqObjCmd.EXPR_SYMBOL:
			expr.symbolId = Short.toUnsignedInt(input.nextShort());
			break;
		case PsyqObjCmd.EXPR_SECBASE:
		case PsyqObjCmd.EXPR_SECSTART:
		case PsyqObjCmd.EXPR_SECEND:
			expr.sectionId = Short.toUnsignedInt(input.nextShort());
			break;
		case PsyqObjCmd.EXPR_ADD:
			expr.right = parse(input);
			expr.left = parse(input);
			if(expr.right.command == PsyqObjCmd.EXPR_ADD) {
				if(expr.left.command == PsyqObjCmd.EXPR_VALUE) {
					if(expr.right.right.command == PsyqObjCmd.EXPR_VALUE) {
						expr.right.right.value += expr.left.value;
						return expr.right;
					}
					else if(expr.right.left.command == PsyqObjCmd.EXPR_VALUE) {
						expr.right.left.value += expr.left.value;
						return expr.right;
					}
				}
			}
			else if(expr.left.command == PsyqObjCmd.EXPR_ADD) {
				if(expr.right.command == PsyqObjCmd.EXPR_VALUE) {
					if(expr.left.right.command == PsyqObjCmd.EXPR_VALUE) {
						expr.left.right.value += expr.right.value;
						return expr.left;
					}
					else if(expr.left.left.command == PsyqObjCmd.EXPR_VALUE) {
						expr.left.left.value += expr.right.value;
						return expr.left;
					}
				}
			}
			if((expr.right == null) || (expr.left == null)) return null;
			break;
		case PsyqObjCmd.EXPR_SUB:
		case PsyqObjCmd.EXPR_DIV:
			expr.right = parse(input);
			expr.left = parse(input);
			if((expr.right == null) || (expr.left == null)) return null;
			break;
		default: return null;
		}
		
		return expr;
	}
	
	public int getCommand() {return command;}
	public int getValue() {return value;}
	public int getSymbolId() {return symbolId;}
	public int getSectionId() {return sectionId;}
	public PsyqExpr getRight() {return right;}
	public PsyqExpr getLeft() {return left;}
	
	public void writeToXML(Writer writer, String indent) throws IOException {
		if(indent == null) indent = "";
		
		writer.write(indent + "<Expression");
		writer.write(String.format(" Op=\"%s\"", PsyqObjCmd.expr2String(command)));
		
		switch(command) {
		case PsyqObjCmd.EXPR_VALUE:
			writer.write(String.format(" Value=\"0x%x\"/>\n", value));
			break;
		case PsyqObjCmd.EXPR_SYMBOL:
			writer.write(String.format(" SymbolId=\"0x%04x\"/>\n", symbolId));
			break;
		case PsyqObjCmd.EXPR_SECBASE:
		case PsyqObjCmd.EXPR_SECSTART:
		case PsyqObjCmd.EXPR_SECEND:
			writer.write(String.format(" SectionId=\"0x%04x\"/>\n", sectionId));
			break;
		case PsyqObjCmd.EXPR_ADD:
		case PsyqObjCmd.EXPR_SUB:
		case PsyqObjCmd.EXPR_DIV:
			writer.write(">\n");
			if(right != null) {
				writer.write(indent + "\t<RightNode>\n");
				right.writeToXML(writer, indent + "\t\t");
				writer.write(indent + "\t</RightNode>\n");
			}
			if(left != null) {
				writer.write(indent + "\t<LeftNode>\n");
				left.writeToXML(writer, indent + "\t\t");
				writer.write(indent + "\t</LeftNode>\n");
			}
			writer.write(indent + "</Expression>\n");
			break;
		default:
			writer.write("/>\n");
			break;
		}
		
	}

}
