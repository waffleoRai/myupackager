package waffleoRai_extractMyu.mains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import waffleoRai_Containers.CDTable.CDInvalidRecordException;
import waffleoRai_Containers.ISO;
import waffleoRai_Containers.ISOXAImage;
import waffleoRai_Containers.XATable;
import waffleoRai_Containers.XATable.XAEntry;
import waffleoRai_Files.tree.DirectoryNode;
import waffleoRai_Files.tree.FileNode;
import waffleoRai_Utils.FileBuffer;
import waffleoRai_Utils.FileBuffer.UnsupportedFileTypeException;
import waffleoRai_Utils.FileUtils;
import waffleoRai_Utils.MultiFileBuffer;
import waffleoRai_extractMyu.LiteNode;
import waffleoRai_extractMyu.MyuArcCommon;
import waffleoRai_extractMyu.MyuPackagerLogger;
import waffleoRai_extractMyu.MyupkgConstants;
import waffleoRai_extractMyu.mains.ArcExtract.ArcExtractContext;

public class IsoExtract {

	public static void main_isoUnpack(Map<String, String> args) throws IOException, CDInvalidRecordException, UnsupportedFileTypeException{
		String input_path = args.get("iso");
		String output_dir_cd = args.get("cdout");
		String output_dir_asset = args.get("assetout");
		String spec_path = args.get("arcspec"); //Arcspec dir
		String xml_path = args.get("xmlout");
		String checksum_path = args.get("checksums");
		
		//--- Check paths
		boolean is_cue = false;
		if(input_path == null) {
			//Check for cue sheet instead
			input_path = args.get("cue");
			is_cue = true;
			if(input_path == null){
				MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
						"Input file argument is required!");
				return;
			}
			if(!FileBuffer.fileExists(input_path)){
				MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
						"Input file \"" + input_path + "\" does not exist!");
				return;
			}
		}
		
		String input_dir = input_path.substring(0, input_path.lastIndexOf(File.separatorChar));
		
		if(output_dir_cd == null) {
			output_dir_cd = input_dir + File.separator + "cd";
			MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
					"CD files directory was not provided. Creating directory in input folder (" + output_dir_cd + ")");
		}
		if(output_dir_asset == null) {
			output_dir_asset = input_dir + File.separator + "assets";
			MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
					"Assets directory was not provided. Creating directory in input folder (" + output_dir_asset + ")");
		}
		
		if(!FileBuffer.directoryExists(output_dir_cd)) {
			Files.createDirectories(Paths.get(output_dir_cd));
		}
		if(!FileBuffer.directoryExists(output_dir_asset)) {
			Files.createDirectories(Paths.get(output_dir_asset));
		}
		
		if((checksum_path == null) || (!FileBuffer.fileExists(checksum_path))) {
			checksum_path = input_path + File.separator + "checksums.csv";
			MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
					"Checksum table path was not provided, or table does not exist. Setting to " + checksum_path);
		}
		
		//Read CUE file to find actual binary, if applicable
		if(is_cue) {
			MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
					"Attempting to find image binary from CUE file \"" + input_path + "\"");
			input_path = MyuArcCommon.findBIN_fromCUE(input_path);
			if(input_path == null) {
				MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
						"Image BIN could not be found! Returning...");
				return;
			}
		}
		
		//--- Input image checksum (if checksums provided)
		//Here, this is mostly for warning the user if provided image is heretofore unknown and results may not be as they expect.
		String[][] checksums = MyuArcCommon.loadChecksumTable(checksum_path); //Returns null if file doesn't exist.
		if(checksums != null) {
			try {
				MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
						"Calculating SHA-256 of input image...");
				byte[] in_sha = FileBuffer.getFileHash("SHA-256", input_path);
				String shastr = FileUtils.bytes2str(in_sha);
				shastr = shastr.toUpperCase();
				MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
						"Hash: " + shastr);
				
				//Check for match
				boolean found = false;
				for(int i = 0; i < checksums.length; i++) {
					if(checksums[i][0] == null) continue;
					if(checksums[i][2] == null) continue;
					if(!checksums[i][0].equalsIgnoreCase("ISO")) continue;
					if(checksums[i][2].equals(shastr)) {
						MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
								"Image match found: " + checksums[i][1]);
						found = true;
						break;
					}
				}
				
				if(!found) {
					MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
							"WARNING: No match was found to known image. Unpack will continue, but be aware that the results will be unpredictable.");
				}
				
			} catch (NoSuchAlgorithmException e) {
				MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
						"Input file hash failed: internal error (see stack trace)");
				e.printStackTrace();
			} catch (IOException e) {
				MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
						"Input file hash failed: I/O error (see stack trace)");
				e.printStackTrace();
			}
		}
		else {
			MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
					"Checksum table could not be loaded. Skipping input image checksum...");
		}
		
		//Attempt to read CD image
		FileBuffer cdbuff = FileBuffer.createBuffer(input_path, true);
		ISO rawCd = new ISO(cdbuff, true);
		ISOXAImage cd_image = MyuArcCommon.readISOFile(input_path);
		
		//start output XML for CD build
		if(xml_path == null) {
			xml_path = input_dir + File.separator + "buildiso.xml";
			MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
					"Output XML path was not provided. Setting to \"" + xml_path + "\"");
		}
		LiteNode cdbuild_root = new LiteNode();
		cdbuild_root.name = MyupkgConstants.XML_NODENAME_ISOBUILD;
		
		//Generate relative paths for XML use
		String cddir_rel = MyuArcCommon.localPath2UnixRel(xml_path, output_dir_cd);
		String assetdir_rel = MyuArcCommon.localPath2UnixRel(xml_path, output_dir_asset);
		
		//Get metadata from CD
		cdbuild_root.attr.put(MyupkgConstants.XML_ATTR_VOLUMEID, cd_image.getVolumeIdent());
		cdbuild_root.attr.put(MyupkgConstants.XML_ATTR_PUBID, cd_image.getPublisherIdent());
		cdbuild_root.attr.put(MyupkgConstants.XML_ATTR_REGION, "J");
		cdbuild_root.attr.put(MyupkgConstants.XML_ATTR_MATCHMODE, "True");
		cdbuild_root.attr.put(MyupkgConstants.XML_ATTR_FAKETIME, MyuArcCommon.datetime2XMLVal(cd_image.getDateCreated()));
		
		//Extract PSLogo sectors (5-11)
		FileBuffer pslogo = new MultiFileBuffer(7);
		for(int i = 0; i < 7; i++) {
			pslogo.addToFile(rawCd.getSectorRelative(i+5).getData());
		}
		String pslogo_path = output_dir_cd + File.separator + MyupkgConstants.FILENAME_PSLOGO;
		pslogo.writeFile(pslogo_path);
		pslogo.dispose();
		LiteNode childnode = cdbuild_root.newChild(MyupkgConstants.XML_NODENAME_PSLOGO);
		childnode.value = cddir_rel + "/" + MyupkgConstants.FILENAME_PSLOGO;
		
		//Note path tables
		int ptstart = cd_image.getPathTable_1_start();
		LiteNode ptnode = cdbuild_root.newChild(MyupkgConstants.XML_NODENAME_PATHTABLE);
		if(ptstart > 0) {
			ptnode.attr.put(MyupkgConstants.XML_ATTR_STARTSEC, Integer.toString(ptstart));
		}
		ptstart = cd_image.getPathTable_2_start();
		ptnode = cdbuild_root.newChild(MyupkgConstants.XML_NODENAME_PATHTABLE);
		if(ptstart > 0) {
			ptnode.attr.put(MyupkgConstants.XML_ATTR_STARTSEC, Integer.toString(ptstart));
		}
		ptstart = cd_image.getPathTable_3_start();
		ptnode = cdbuild_root.newChild(MyupkgConstants.XML_NODENAME_PATHTABLE);
		if(ptstart > 0) {
			ptnode.attr.put(MyupkgConstants.XML_ATTR_STARTSEC, Integer.toString(ptstart));
		}
		ptstart = cd_image.getPathTable_4_start();
		ptnode = cdbuild_root.newChild(MyupkgConstants.XML_NODENAME_PATHTABLE);
		if(ptstart > 0) {
			ptnode.attr.put(MyupkgConstants.XML_ATTR_STARTSEC, Integer.toString(ptstart));
		}
		
		//Get file table from CD (need to annotate file locations and metadata)
		XATable cd_table = cd_image.getXATable();
		List<XAEntry> cd_table_entries = new LinkedList<XAEntry>();
		cd_table_entries.addAll(cd_table.getXAEntries());
		Collections.sort(cd_table_entries);
		for(XAEntry e : cd_table_entries) {
			if(e.isRawFile()) continue;
			LiteNode enode = cdbuild_root.newChild(MyupkgConstants.XML_NODENAME_CDFILE);
			String fname = e.getName();
			boolean is_arc = false;
			if(fname.startsWith("D_") || fname.endsWith(".STR") || fname.equals("MOVIE.BIN")) {
				is_arc = true;
				enode.attr.put(MyupkgConstants.XML_ATTR_CDFILETYPE, MyupkgConstants.XML_CDFILETYPE_ARC);
			}
			else enode.attr.put(MyupkgConstants.XML_ATTR_CDFILETYPE, MyupkgConstants.XML_CDFILETYPE_FILE);
			
			enode.attr.put(MyupkgConstants.XML_ATTR_FILENAME, fname);
			enode.attr.put(MyupkgConstants.XML_ATTR_STARTSEC, Integer.toString(e.getStartBlock()));
			enode.attr.put(MyupkgConstants.XML_ATTR_FAKETIME, MyuArcCommon.datetime2XMLVal(e.getDate()));
			
			//Perm (write is always unset)
			int perm_u = 0;
			if(e.ownerRead()) perm_u |= 0x4;
			if(e.ownerExecute()) perm_u |= 0x1;
			
			int perm_g = 0;
			if(e.groupRead()) perm_g |= 0x4;
			if(e.groupExecute()) perm_g |= 0x1;
			
			int perm_a = 0;
			if(e.AllRead()) perm_a |= 0x4;
			if(e.AllExecute()) perm_a |= 0x1;
			enode.attr.put(MyupkgConstants.XML_ATTR_FILEPERM, String.format("%d%d%d", perm_u, perm_g, perm_a));
			enode.attr.put(MyupkgConstants.XML_ATTR_OWNERGROUP, Integer.toString(e.getOwnerGroupID()));
			enode.attr.put(MyupkgConstants.XML_ATTR_OWNERUSER, Integer.toString(e.getOwnerUserID()));
			
			//Value
			if(is_arc) {
				//determine name of xml file
				String xmlname = fname;
				int cidx = xmlname.lastIndexOf('.');
				if(cidx >= 0) {
					xmlname = xmlname.substring(0, cidx);
				}
				xmlname = xmlname.replace("D_", "");
				xmlname = xmlname.toLowerCase();
				
				enode.value = assetdir_rel + "/" + xmlname + ".xml";
			}
			else {
				//Put the exe and cfg in the cd dir. 
				//Anything else goes in the asset dir, but the only thing that should include is the cd da stream.
				if(fname.endsWith(".CFG") || fname.startsWith("SLPM")) {
					enode.value = cddir_rel + "/" + fname;
				}
				else enode.value = assetdir_rel + "/" + fname;
			}
		}
	
		
		//Extract sectors that are non-zero and not used by the filesystem(?)
		//Render file tree from CD and extract those files to cd out dir (note that track 2 might not be where it's supposed to be - CUE sheet can specify if present)
		//Run the arc extractors on the archives and streams. (Exe disassembly is handled externally)
		//Clean up ARC bins in cddir that are successfully broken down
		DirectoryNode fs_root = cd_image.getRootNode();
		String fsDirName = cd_image.getVolumeIdent().trim();
		for(LiteNode root_child : cdbuild_root.children) {
			if(root_child.name.equals(MyupkgConstants.XML_NODENAME_CDFILE)) {
				String fname = root_child.attr.get(MyupkgConstants.XML_ATTR_FILENAME);
				MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
						"Extracting " + fname + " from CD image...");
				FileNode cd_file = fs_root.getNodeAt("./" + fsDirName + "/"+ fname);
				if(cd_file == null) {
					MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
							"ERROR -- (Internal) File was found in ISO table, but node was not generated! Skipping...");
					continue;
				}
				
				//Try to load the file using the node. If it's the Track 2 file, make sure that it's actually there...
				FileBuffer filedata = null;
				try {
					filedata = cd_file.loadData();
				}
				catch(Exception ex) {
					MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
							"ERROR -- Data could not be loaded from CD image!");
					continue;
				}
				
				
				String aval = root_child.attr.get(MyupkgConstants.XML_ATTR_CDFILETYPE);
				if(aval.equals(MyupkgConstants.XML_CDFILETYPE_ARC)) {
					//Copy to disk in CD dir
					String extpath = output_dir_cd + File.separator + fname;
					filedata.writeFile(extpath);
					MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
							"Raw arc extracted to: " + extpath);
					
					//Run the extract archive on extracted file
					//Check for spec xml. If not there, just copy the full arc to assets dir
					String dname = fname;
					if(dname.contains(".")) dname = dname.substring(0, dname.lastIndexOf('.'));
					dname = dname.replace("D_", "");
					dname = dname.toLowerCase();
					String spec_xml_path = spec_path + File.separator + dname + ".xml";
					if(FileBuffer.fileExists(spec_xml_path)) {
						MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
								"Arc spec file found! Attempting unpack...");
						
						ArcExtractContext aectx = new ArcExtractContext();
						aectx.input_path = extpath;
						aectx.output_dir = output_dir_asset + File.separator + dname;
						aectx.rel_path = assetdir_rel + "/" + dname;
						aectx.spec_path = spec_xml_path;
						aectx.xml_path = output_dir_asset + File.separator + dname + ".xml";
						
						ArcExtract.unpackArchive(aectx);
					}
					else {
						MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
								"Arc spec file not found. Skipping extraction...");
						Files.copy(Paths.get(extpath), Paths.get(output_dir_asset + File.separator + fname));
						root_child.attr.put(MyupkgConstants.XML_ATTR_CDFILETYPE, MyupkgConstants.XML_CDFILETYPE_FILE);
						root_child.value = assetdir_rel + "/" + fname;
					}
					
					//Delete arc file
					MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
							"Archive extraction complete. Deleting " + extpath + "...");
					Files.delete(Paths.get(extpath));
				}
				else {
					//Copy to disk
					String extpath = MyuArcCommon.unixRelPath2Local(xml_path, root_child.value);
					filedata.writeFile(extpath);
					MyuPackagerLogger.logMessage("IsoExtract.main_isoUnpack", 
							"Extracted to: " + extpath);
				}
				
			}
		}
		
	}
	
}
