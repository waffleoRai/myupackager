package waffleoRai_extractMyu;

import java.util.HashMap;
import java.util.Map;

import waffleoRai_soundbank.vab.PSXVAB;
import waffleoRai_soundbank.vab.VABProgram;
import waffleoRai_soundbank.vab.VABTone;

public class SoundbankXML {
	
	public static final String XML_VABNODE_VAB = "Vab";
	public static final String XML_VABNODE_VH = "VabHeader";
	public static final String XML_VABNODE_VB = "VabBody";
	public static final String XML_VABNODE_PROG = "Program";
	public static final String XML_VABNODE_TONE = "Tone";
	
	public static final String XML_VABNODE_ADSR = "ADSR";
	public static final String XML_VABNODE_TUNING = "ToneTune";
	public static final String XML_VABNODE_VIB = "Vibrato";
	public static final String XML_VABNODE_PORTA = "Portamento";
	public static final String XML_VABNODE_PITCHBEND = "PitchBend";
	
	public static final String XML_VABATTR_SAMPNAME = "SampleName";
	
	public static final String XML_VABATTR_VER = "Version";
	public static final String XML_VABATTR_VABID = "VabId";
	public static final String XML_VABATTR_MVOL = "MasterVol";
	public static final String XML_VABATTR_MPAN = "MasterPan";
	public static final String XML_VABATTR_BANKATTR1 = "BankAttr1";
	public static final String XML_VABATTR_BANKATTR2 = "BankAttr2";
	
	public static final String XML_VABATTR_PVOL = "Volume";
	public static final String XML_VABATTR_PPAN = "Pan";
	public static final String XML_VABATTR_PPRI = "Priority";
	public static final String XML_VABATTR_PMODE = "Mode";
	public static final String XML_VABATTR_PATTR = "ProgAttr";
	public static final String XML_VABATTR_PIDX = "ProgIndex";
	
	public static final String XML_VABATTR_TVOL = "Volume";
	public static final String XML_VABATTR_TPAN = "Pan";
	public static final String XML_VABATTR_TPRI = "Priority";
	public static final String XML_VABATTR_TMODE = "Mode";
	public static final String XML_VABATTR_TUNITYKEY = "UnityKey";
	public static final String XML_VABATTR_TPITCHTUNE = "FineTune";
	public static final String XML_VABATTR_TNOTEMIN = "NoteMin";
	public static final String XML_VABATTR_TNOTEMAX = "NoteMax";
	public static final String XML_VABATTR_TVIBWIDTH = "VibWidth";
	public static final String XML_VABATTR_TVIBTIME = "VibTime";
	public static final String XML_VABATTR_TPORTWIDTH = "PortaWidth";
	public static final String XML_VABATTR_TPORTTIME = "PortaTime";
	public static final String XML_VABATTR_TPBMIN = "PitchbendMin";
	public static final String XML_VABATTR_TPBMAX = "PitchbendMax";
	public static final String XML_VABATTR_TSAMPLE = "Sample";

	public static final String XML_VABATTR_ATTACK = "Attack";
	public static final String XML_VABATTR_DECAY = "Decay";
	public static final String XML_VABATTR_SUSTAIN = "Sustain";
	public static final String XML_VABATTR_RELEASE = "Release";
	public static final String XML_VABATTR_ADSR_MODE = "Mode";
	public static final String XML_VABATTR_ADSR_STEP = "Step";
	public static final String XML_VABATTR_ADSR_SHIFT = "Shift";
	public static final String XML_VABATTR_ADSR_DIR = "Direction";
	public static final String XML_VABATTR_ADSR_LEVEL = "Level";
	
	public static final String XML_VABATTR_ADSR_MTYPE_LIN = "Linear";
	public static final String XML_VABATTR_ADSR_MTYPE_PEXP = "PseudoExp";
	public static final String XML_VABATTR_ADSR_MTYPE_EXP = "Exponential";
	public static final String XML_VABATTR_ADSR_DTYPE_UP = "Increase";
	public static final String XML_VABATTR_ADSR_DTYPE_DOWN = "Decrease";
	
	public static LiteNode getFirstChildWithName(LiteNode node, String name) {
		if(node == null || name == null) return null;
		for(LiteNode child : node.children) {
			if(name.equals(child.name)) return child;
		}
		return null;
	}
	
	public static LiteNode exportSoundbankHead(PSXVAB vab, String[] sampleNames){
		LiteNode node = new LiteNode();
		node.name = XML_VABNODE_VH;
		
		//Top level attr
		node.attr.put(XML_VABATTR_VER, Integer.toString(vab.getVersion()));
		node.attr.put(XML_VABATTR_VABID, Integer.toString(vab.getID()));
		node.attr.put(XML_VABATTR_MVOL, Integer.toString(vab.getVolume()));
		node.attr.put(XML_VABATTR_MPAN, Integer.toString(vab.getPan()));
		node.attr.put(XML_VABATTR_BANKATTR1, Integer.toString(vab.getAttribute1()));
		node.attr.put(XML_VABATTR_BANKATTR2, Integer.toString(vab.getAttribute2()));
		
		//Programs
		for(int i = 0; i < 128; i++){
			LiteNode prog_node = new LiteNode();
			node.children.add(prog_node);
			prog_node.parent = node;
			prog_node.name = XML_VABNODE_PROG;
			
			//Program-wide stuff
			VABProgram program = vab.getProgram(i);
			if(program == null) continue;
			prog_node.attr.put(XML_VABATTR_PIDX, Integer.toString(i));
			prog_node.attr.put(XML_VABATTR_PVOL, Integer.toString(program.getVolume()));
			prog_node.attr.put(XML_VABATTR_PPAN, Integer.toString(program.getPan()));
			prog_node.attr.put(XML_VABATTR_PPRI, Integer.toString(program.getPriority()));
			prog_node.attr.put(XML_VABATTR_PMODE, String.format("0x%02x", program.getMode()));
			prog_node.attr.put(XML_VABATTR_PATTR, Integer.toString(program.getAttribute()));
			
			//Tones
			for(int j = 0; j < 16; j++){
				LiteNode tone_node = new LiteNode();
				prog_node.children.add(tone_node);
				tone_node.parent = prog_node;
				tone_node.name = XML_VABNODE_TONE;
				
				//Main tone attr
				VABTone tone = program.getTone(j);
				if(tone == null) continue;
				tone_node.attr.put(XML_VABATTR_TVOL, Integer.toString(tone.getVolume()));
				tone_node.attr.put(XML_VABATTR_TPAN, Integer.toString(tone.getPan()));
				tone_node.attr.put(XML_VABATTR_TPRI, Integer.toString(tone.getPriority()));
				if(tone.hasReverb()){
					tone_node.attr.put(XML_VABATTR_TMODE, "0x04");
				}
				else{
					tone_node.attr.put(XML_VABATTR_TMODE, "0x00");
				}
				
				int sampleidx = tone.getSampleIndex() - 1;
				if(sampleNames != null){
					tone_node.attr.put(XML_VABATTR_TSAMPLE, sampleNames[sampleidx]);
				} else{
					tone_node.attr.put(XML_VABATTR_TSAMPLE, String.format("vb_%03d", sampleidx));
				}
				
				LiteNode tone_child = tone_node.newChild(XML_VABNODE_TUNING);
				tone_child.attr.put(XML_VABATTR_TUNITYKEY, Integer.toString(tone.getUnityKey()));
				tone_child.attr.put(XML_VABATTR_TPITCHTUNE, Integer.toString(tone.getFineTune()));
				tone_child.attr.put(XML_VABATTR_TNOTEMIN, Integer.toString(tone.getKeyRangeBottom()));
				tone_child.attr.put(XML_VABATTR_TNOTEMAX, Integer.toString(tone.getKeyRangeTop()));
				tone_child.attr.put(XML_VABATTR_TPBMIN, Integer.toString(tone.getPitchBendMin()));
				tone_child.attr.put(XML_VABATTR_TPBMAX, Integer.toString(tone.getPitchBendMax()));
				
				tone_child = tone_node.newChild(XML_VABNODE_VIB);
				tone_child.attr.put(XML_VABATTR_TVIBWIDTH, Integer.toString(tone.getVibratoWidth()));
				tone_child.attr.put(XML_VABATTR_TVIBTIME, Integer.toString(tone.getVibratoTime()));
				
				tone_child = tone_node.newChild(XML_VABNODE_PORTA);
				tone_child.attr.put(XML_VABATTR_TPORTWIDTH, Integer.toString(tone.getPortamentoWidth()));
				tone_child.attr.put(XML_VABATTR_TPORTTIME, Integer.toString(tone.getPortamentoTime()));
				
				//ADSR
				LiteNode adsr_node = tone_node.newChild(XML_VABNODE_ADSR);
				tone_child = adsr_node.newChild(XML_VABATTR_ATTACK);
				tone_child.attr.put(XML_VABATTR_ADSR_SHIFT, Integer.toString(tone.getAttackShift()));
				tone_child.attr.put(XML_VABATTR_ADSR_STEP, Integer.toString(tone.getAttackStep()));
				if(tone.getAttackMode()){
					tone_child.attr.put(XML_VABATTR_ADSR_MODE, XML_VABATTR_ADSR_MTYPE_PEXP);
				}
				else{
					tone_child.attr.put(XML_VABATTR_ADSR_MODE, XML_VABATTR_ADSR_MTYPE_LIN);
				}
				
				tone_child = adsr_node.newChild(XML_VABATTR_DECAY);
				tone_child.attr.put(XML_VABATTR_ADSR_SHIFT, Integer.toString(tone.getDecayShift()));
				
				tone_child = adsr_node.newChild(XML_VABATTR_SUSTAIN);
				tone_child.attr.put(XML_VABATTR_ADSR_SHIFT, Integer.toString(tone.getSustainShift()));
				tone_child.attr.put(XML_VABATTR_ADSR_STEP, Integer.toString(tone.getSustainStep()));
				tone_child.attr.put(XML_VABATTR_ADSR_LEVEL, Integer.toString(tone.getSustainLevel()));
				
				if(tone.getSustainDirection()){
					tone_child.attr.put(XML_VABATTR_ADSR_DIR, XML_VABATTR_ADSR_DTYPE_DOWN);
				}
				else{
					tone_child.attr.put(XML_VABATTR_ADSR_DIR, XML_VABATTR_ADSR_DTYPE_UP);
				}
				
				if(tone.getSustainMode()){
					if(tone.getSustainDirection()){
						tone_child.attr.put(XML_VABATTR_ADSR_MODE, XML_VABATTR_ADSR_MTYPE_EXP);
					}
					else{
						tone_child.attr.put(XML_VABATTR_ADSR_MODE, XML_VABATTR_ADSR_MTYPE_PEXP);
					}
				}
				else{
					tone_child.attr.put(XML_VABATTR_ADSR_MODE, XML_VABATTR_ADSR_MTYPE_LIN);
				}
				
				tone_child = adsr_node.newChild(XML_VABATTR_RELEASE);
				tone_child.attr.put(XML_VABATTR_ADSR_SHIFT, Integer.toString(tone.getReleaseShift()));
				if(tone.getReleaseMode()){
					tone_child.attr.put(XML_VABATTR_ADSR_MODE, XML_VABATTR_ADSR_MTYPE_EXP);
				}
				else{
					tone_child.attr.put(XML_VABATTR_ADSR_MODE, XML_VABATTR_ADSR_MTYPE_LIN);
				}
			}
		}

		return node;
	}

	private static void readToneNode(LiteNode toneNode, VABTone dst) {
		if(toneNode == null || dst == null) return;
		
		String aval = toneNode.attr.get(XML_VABATTR_TVOL);
		if(aval != null) dst.setVolume(Integer.parseInt(aval));
		aval = toneNode.attr.get(XML_VABATTR_TPAN);
		if(aval != null) dst.setPan(Integer.parseInt(aval));
		aval = toneNode.attr.get(XML_VABATTR_TPRI);
		if(aval != null) dst.setPriority(Integer.parseInt(aval));
		
		aval = toneNode.attr.get(XML_VABATTR_TMODE);
		if(aval != null) {
			int val = 0;
			if(aval.startsWith("0x")) val = Integer.parseInt(aval.substring(2), 16);
			else val = Integer.parseInt(aval);
			dst.setReverb(val != 0);
		}
		
		LiteNode child = getFirstChildWithName(toneNode, XML_VABNODE_TUNING);
		if(child != null) {
			aval = child.attr.get(XML_VABATTR_TUNITYKEY);
			if(aval != null) dst.setUnityKey(Integer.parseInt(aval));
			aval = child.attr.get(XML_VABATTR_TPITCHTUNE);
			if(aval != null) dst.setFineTune256(Integer.parseInt(aval));
			
			int minNote = 0; int maxNote = 127;
			aval = child.attr.get(XML_VABATTR_TNOTEMIN);
			if(aval != null) minNote = Integer.parseInt(aval);
			aval = child.attr.get(XML_VABATTR_TNOTEMAX);
			if(aval != null) maxNote = Integer.parseInt(aval);
			dst.setKeyRange(minNote, maxNote);
			
			int min = 2; int max = 2;
			aval = child.attr.get(XML_VABATTR_TPBMIN);
			if(aval != null) min = Integer.parseInt(aval);
			aval = child.attr.get(XML_VABATTR_TPBMAX);
			if(aval != null) max = Integer.parseInt(aval);
			dst.setPitchBend(min, max);
		}
		
		child = getFirstChildWithName(toneNode, XML_VABNODE_VIB);
		if(child != null) {
			int w = 0; int t = 0;
			aval = child.attr.get(XML_VABATTR_TVIBWIDTH);
			if(aval != null) w = Integer.parseInt(aval);
			aval = child.attr.get(XML_VABATTR_TVIBTIME);
			if(aval != null) t = Integer.parseInt(aval);
			
			dst.setVibrato(w, t);
		}
		
		child = getFirstChildWithName(toneNode, XML_VABNODE_PORTA);
		if(child != null) {
			int w = 0; int t = 0;
			aval = child.attr.get(XML_VABATTR_TPORTWIDTH);
			if(aval != null) w = Integer.parseInt(aval);
			aval = child.attr.get(XML_VABATTR_TPORTTIME);
			if(aval != null) t = Integer.parseInt(aval);
			
			dst.setPortamento(w, t);
		}
		
		child = getFirstChildWithName(toneNode, XML_VABNODE_ADSR);
		if(child != null) {
			LiteNode gchild = getFirstChildWithName(child, XML_VABATTR_ATTACK);
			if(gchild != null) {
				String mode = gchild.attr.get(XML_VABATTR_ADSR_MODE);
				int am = 0;
				if(mode != null && mode.equals(XML_VABATTR_ADSR_MTYPE_PEXP)) am = 1;
				
				int shift = 0; int step = 0;
				aval = gchild.attr.get(XML_VABATTR_ADSR_STEP);
				if(aval != null) step = Integer.parseInt(aval);
				aval = gchild.attr.get(XML_VABATTR_ADSR_SHIFT);
				if(aval != null) shift = Integer.parseInt(aval);
				
				dst.setAttack(am != 0, shift, step);
			}
			
			gchild = getFirstChildWithName(child, XML_VABATTR_DECAY);
			if(gchild != null) {
				int shift = 0;
				aval = gchild.attr.get(XML_VABATTR_ADSR_SHIFT);
				if(aval != null) shift = Integer.parseInt(aval);
				
				dst.setDecay(shift);
			}
			
			gchild = getFirstChildWithName(child, XML_VABATTR_SUSTAIN);
			if(gchild != null) {
				String modestr = gchild.attr.get(XML_VABATTR_ADSR_MODE);
				boolean mode = false;
				if(modestr != null && (modestr.equals(XML_VABATTR_ADSR_MTYPE_PEXP) || (modestr.equals(XML_VABATTR_ADSR_MTYPE_EXP)))) mode = true;
				String dirstr = gchild.attr.get(XML_VABATTR_ADSR_DIR);
				boolean dir = false;
				if(dirstr != null && dirstr.equals(XML_VABATTR_ADSR_DTYPE_DOWN)) dir = true;
				
				int shift = 0; int step = 0; int level = 0;
				aval = gchild.attr.get(XML_VABATTR_ADSR_STEP);
				if(aval != null) step = Integer.parseInt(aval);
				aval = gchild.attr.get(XML_VABATTR_ADSR_SHIFT);
				if(aval != null) shift = Integer.parseInt(aval);
				aval = gchild.attr.get(XML_VABATTR_ADSR_LEVEL);
				if(aval != null) level = Integer.parseInt(aval);
				
				dst.setSustainRate(mode, shift, step, dir);
				dst.setSustainLevel(level);
			}
			
			gchild = getFirstChildWithName(child, XML_VABATTR_RELEASE);
			if(gchild != null) {
				String mstr = gchild.attr.get(XML_VABATTR_ADSR_MODE);
				boolean mode = false;
				if(mstr != null && mstr.equals(XML_VABATTR_ADSR_MTYPE_EXP)) mode = true;
				
				int shift = 0;
				aval = gchild.attr.get(XML_VABATTR_ADSR_SHIFT);
				if(aval != null) shift = Integer.parseInt(aval);
				
				dst.setRelease(mode, shift);
			}
		}
	}
	
	public static PSXVAB importSoundbankHead(LiteNode headNode, Map<String, MyuSoundSample> sampleMap){
		int ival = 0;
		String aval = headNode.attr.get(XML_VABATTR_VABID);
		if(aval != null){
			try{ival = Integer.parseInt(aval);}
			catch(NumberFormatException ex){ex.printStackTrace();}
		}
		
		PSXVAB vab = new PSXVAB(ival);
		try{
			//VAB header common fields
			aval = headNode.attr.get(XML_VABATTR_VER);
			if(aval != null) {vab.setVersion(Integer.parseInt(aval));}
			
			aval = headNode.attr.get(XML_VABATTR_MVOL);
			if(aval != null) {vab.setMasterVolume(Byte.parseByte(aval));}
			
			aval = headNode.attr.get(XML_VABATTR_MPAN);
			if(aval != null) {vab.setMasterPan(Byte.parseByte(aval));}
			
			aval = headNode.attr.get(XML_VABATTR_BANKATTR1);
			if(aval != null) {vab.setBankAttr1(Byte.parseByte(aval));}
			
			aval = headNode.attr.get(XML_VABATTR_BANKATTR2);
			if(aval != null) {vab.setBankAttr2(Byte.parseByte(aval));}
			
			//Go through program child nodes...
			for(LiteNode child : headNode.children) {
				if(child.name == null) continue;
				if(child.name.equals(XML_VABNODE_PROG)) {
					if(!child.attr.isEmpty()) {
						int p = 0;
						aval = child.attr.get(XML_VABATTR_PIDX);
						if(aval != null) {p = Integer.parseInt(aval);}
						
						VABProgram program = vab.newProgram(p);
						aval = child.attr.get(XML_VABATTR_PVOL);
						if(aval != null) {program.setVolume(Integer.parseInt(aval));}
						aval = child.attr.get(XML_VABATTR_PPAN);
						if(aval != null) {program.setPan(Integer.parseInt(aval));}
						aval = child.attr.get(XML_VABATTR_PPRI);
						if(aval != null) {program.setPriority(Integer.parseInt(aval));}
						aval = child.attr.get(XML_VABATTR_PATTR);
						if(aval != null) {program.setAttribute(Integer.parseInt(aval));}
						
						aval = child.attr.get(XML_VABATTR_PMODE);
						if(aval != null) {
							if(aval.startsWith("0x")) {
								program.setMode(Integer.parseInt(aval.substring(2), 16));
							}
							else program.setMode(Integer.parseInt(aval));
						}
						
						//Tones
						int t = 0;
						for(LiteNode gchild : child.children) {
							if(gchild.name == null) continue;
							if(gchild.name.equals(XML_VABNODE_TONE)) {
								if(!gchild.attr.isEmpty()) {
									VABTone tone = new VABTone(t);
									readToneNode(gchild, tone);
									tone.setParentProgram(p);
									
									String sampleName = gchild.attr.get(XML_VABATTR_TSAMPLE);
									if(sampleName != null) {
										MyuSoundSample smp = sampleMap.get(sampleName);
										if(smp != null) tone.setSampleIndex(smp.getIndex()+1);
									}
									program.setTone(tone, t);
								}
								t++;
							}
						}
					}
				}
			}
			
		}
		catch(Exception ex){
			ex.printStackTrace();
		}
		
		return vab;
	}

	public static Map<String, MyuSoundSample> importSoundbankBody(LiteNode bodyNode) {
		Map<String, MyuSoundSample> sampleMap = new HashMap<String, MyuSoundSample>();
		if(bodyNode == null) return sampleMap;
		
		int i = 0;
		for(LiteNode child : bodyNode.children) {
			if(child.name == null) continue;
			if(!child.name.equals(MyupkgConstants.ASSET_TYPE_SOUNDSAMP)) continue;
			MyuSoundSample smpl = new MyuSoundSample();
			smpl.setIndex(i);
			smpl.setName(child.attr.get(XML_VABATTR_SAMPNAME));
			smpl.setRelPath(child.value);
			
			sampleMap.put(smpl.getName(), smpl);
			i++;
		}
		
		return sampleMap;
	}
	
}
