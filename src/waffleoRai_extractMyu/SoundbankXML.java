package waffleoRai_extractMyu;

import java.util.Map;

import waffleoRai_soundbank.vab.PSXVAB;

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
	public static final String XML_VABATTR_PIDX = "ProgIndex"; //MyuPkg doesn't need this, this is more for the user.
	
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
			PSXVAB.Program program = vab.getProgram(i);
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
				PSXVAB.Tone tone = program.getTone(j);
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
				
				int sampleidx = tone.getSampleIndex();
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

	public static PSXVAB importSoundbankHead(LiteNode node, Map<String, MyuSoundSample> sampleMap){
		//TODO
		int ival = 0;
		String aval = node.attr.get(XML_VABATTR_VABID);
		if(aval != null){
			try{ival = Integer.parseInt(aval);}
			catch(NumberFormatException ex){ex.printStackTrace();}
		}
		
		PSXVAB vab = new PSXVAB(ival);
		try{
			//VAB header common fields
			
			//Go through program child nodes...
		}
		catch(Exception ex){
			ex.printStackTrace();
		}
		
		return null;
	}
	
}
