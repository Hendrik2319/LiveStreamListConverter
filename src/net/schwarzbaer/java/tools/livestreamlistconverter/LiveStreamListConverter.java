package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import net.schwarzbaer.java.lib.gui.Disabler;
import net.schwarzbaer.java.lib.gui.FileChooser;
import net.schwarzbaer.java.lib.gui.GUI;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.StandardMainWindow;

public final class LiveStreamListConverter implements ActionListener {
	
	// http://yp.shoutcast.com/sbin/tunein-station.pls?id=709938   [ DigitalGunfire.com ]
	// http://yp.shoutcast.com/sbin/tunein-station.pls?id=1591787  Sanctuary Radio
	// http://yp.shoutcast.com/sbin/tunein-station.pls?id=359487   -=- tormented radio -=-
	// http://yp.shoutcast.com/sbin/tunein-station.pls?id=180864   -=RantRadio Industrial=-
	// http://yp.shoutcast.com/sbin/tunein-station.pls?id=56499    sdx's synthetic experience!
	// http://yp.shoutcast.com/sbin/tunein-station.pls?id=560047   : ampedOut :
	
	private static final String FILENAME_BASECONFIG    = "LiveStreamListConverter.FileLocations.cfg";
	private static final String FILENAME_STATIONS_LIST = "LiveStreamListConverter.KnownStations.cfg";

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		final LiveStreamListConverter converter = new LiveStreamListConverter();
		converter.readBaseConfig();
		converter.updateGUIAccess();
		
		boolean flag_automatic = false;
		boolean flag_keep_gui  = false;
		
		for (int i=0; i<args.length; i++) {
			if (args[i].equalsIgnoreCase("-automatic")) flag_automatic = true;
			if (args[i].equalsIgnoreCase("-keepgui")) flag_keep_gui = true;
		}
		
		if (flag_automatic) {
			ProgressDialog.runWithProgressDialog(converter.mainWindow, "Progress", 200, pd -> {
				converter.enableGUI(false);
				if (!pd.wasCanceled()) converter.importStationAdressesTask(pd);
				if (!pd.wasCanceled()) converter.createETS2fileTask(pd);
				if (!pd.wasCanceled()) converter.createPlaylistFileTask(pd);
				converter.enableGUI(true);
			});
			
			if (!flag_keep_gui) converter.mainWindow.dispose();
		}
	}
	
	enum ActionCommands
	{
		SelectETS2File, SelectPlaylistFile,
		GotoETS2File, GotoPlaylistFile,
		CreateETS2File, CreatePlaylistFile,
		ImportStationAdresses, EditConfigFiles,
	}

	private final StandardMainWindow mainWindow;
	private final JTextField ets2listFileNameTextField;
	private final JTextField playlistFileNameTextField;
	private final JTextArea ets2listContentTextArea;
	private final JTextArea stationListTextArea;
	private final JTextArea playlistContentTextArea;
	private final FileChooser ets2listFileChooser;
	private final FileChooser playlistFileChooser;
	private File ets2listFile;
	private File playlistFile;
	private String texteditorPath;
	private Vector<Station> stationList;
	private Vector<StreamAdress> adressList;
	private String filemanagerPath;
	private final Disabler<ActionCommands> disabler;
	
	public LiveStreamListConverter() {
		ets2listFile = null;
		playlistFile = null;
		texteditorPath = null;
		filemanagerPath = null;
		stationList = new Vector<>();
		adressList = new Vector<>();
		
		disabler = new Disabler<>();
		disabler.setCareFor(ActionCommands.values());
		
		ets2listFileChooser = new FileChooser("SII file(*.sii)","sii");
		playlistFileChooser = new FileChooser("PLS file(*.pls)","pls");
		
		JPanel fileLabelPanel = new JPanel(new GridLayout(2,1,3,3));
		JPanel fileNamePanel = new JPanel(new GridLayout(2,1,3,3));
		JPanel fileSelectButtonPanel = new JPanel(new GridLayout(2,1,3,3));
		JPanel gotoFolderButtonPanel = new JPanel(new GridLayout(2,1,3,3));
		
		ets2listFileNameTextField = GUI.createOutputTextField(20);
		playlistFileNameTextField = GUI.createOutputTextField(20);
		GUI.addLabelAndField(fileLabelPanel, fileNamePanel, "ETS2 file", ets2listFileNameTextField);
		GUI.addLabelAndField(fileLabelPanel, fileNamePanel, "playlist" , playlistFileNameTextField);
		fileSelectButtonPanel.add(createButton("select", ActionCommands.SelectETS2File));
		fileSelectButtonPanel.add(createButton("select", ActionCommands.SelectPlaylistFile));
		gotoFolderButtonPanel.add(createButton("open folder", filemanagerPath!=null, ActionCommands.GotoETS2File));
		gotoFolderButtonPanel.add(createButton("open folder", filemanagerPath!=null, ActionCommands.GotoPlaylistFile));
		
		JPanel addressListButtonPanel = new JPanel();
		addressListButtonPanel.setLayout(new BoxLayout(addressListButtonPanel,BoxLayout.X_AXIS));
		addressListButtonPanel.add(createButton("import station adresses", ActionCommands.ImportStationAdresses));
		addressListButtonPanel.add(createButton("edit config files", texteditorPath!=null, ActionCommands.EditConfigFiles));
		
		JPanel fileSelectPanel = new JPanel(new BorderLayout(3,3));
		fileSelectPanel.add(fileLabelPanel,BorderLayout.WEST);
		fileSelectPanel.add(fileNamePanel,BorderLayout.CENTER);
		fileSelectPanel.add(GUI.createLeftAlignedPanel(fileSelectButtonPanel,gotoFolderButtonPanel,3),BorderLayout.EAST);
		fileSelectPanel.add(addressListButtonPanel,BorderLayout.SOUTH);
		
		stationListTextArea = createContentTextArea();
		ets2listContentTextArea = createContentTextArea();
		playlistContentTextArea = createContentTextArea();
		
		JPanel fileContentPanel = new JPanel(new GridLayout(0,1,3,3));
		JPanel fileOutputContentPanel = new JPanel(new GridLayout(1,0,3,3));
		fileContentPanel      .add(wrapScrollView(stationListTextArea    ,600,200));
		fileOutputContentPanel.add(wrapScrollView(ets2listContentTextArea,300,200));
		fileOutputContentPanel.add(wrapScrollView(playlistContentTextArea,300,200));
		fileContentPanel.add(fileOutputContentPanel);
		
		JPanel buttonPanel = new JPanel(new GridLayout(1,0,3,3));
		buttonPanel.add(createButton("create ETS2 file", ActionCommands.CreateETS2File));
		buttonPanel.add(createButton("create playlist file", ActionCommands.CreatePlaylistFile));
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
		contentPane.add(fileSelectPanel,BorderLayout.NORTH);
		contentPane.add(fileContentPanel,BorderLayout.CENTER);
		contentPane.add(buttonPanel,BorderLayout.SOUTH);
		
		mainWindow = new StandardMainWindow("Livestream List Converter");
		mainWindow.startGUI(contentPane);
		mainWindow.setSizeAsMinSize();
	}

	private void updateGUIAccess() {
		enableGUI(true);
	}

	private void enableGUI(boolean enable) {
		disabler.setEnable(ac ->  switch (ac) {
			case CreateETS2File, CreatePlaylistFile,
				SelectETS2File, SelectPlaylistFile,
				ImportStationAdresses
				-> enable;
				
			case EditConfigFiles
				-> enable && texteditorPath!=null;
				
			case GotoETS2File, GotoPlaylistFile
				-> enable && filemanagerPath!=null;
		});
	}
	
	private JComponent wrapScrollView(JTextArea textArea, int width, int height) {
		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.getViewport().setPreferredSize(new Dimension(width,height));
		return scrollPane;
	}

	private JButton createButton(String title, ActionCommands ac)
	{
		return createButton( title, true, ac );
	}
	private JButton createButton(String title, boolean enabled, ActionCommands ac)
	{
		JButton comp = new JButton(title);
		comp.setEnabled(enabled);
		comp.addActionListener(this);
		comp.setActionCommand( ac.name() );
		disabler.add(ac, comp);
		return comp;
	}

	private static JTextArea createContentTextArea() {
		JTextArea textArea = new JTextArea(/*rows,columns*/);
		//textArea.setBorder(BorderFactory.createEtchedBorder());
		textArea.setEditable(false);
		return textArea;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		ActionCommands actionCommand;
		try { actionCommand = ActionCommands.valueOf(e.getActionCommand()); }
		catch (Exception e1) { return; }
		
		switch (actionCommand)
		{
		case SelectETS2File:
			if (ets2listFileChooser.showSaveDialog(mainWindow)==JFileChooser.APPROVE_OPTION) {
				ets2listFile = ets2listFileChooser.getSelectedFile();
				ets2listFileNameTextField.setText(ets2listFile.getPath());
				writeBaseConfig();
			}
			break;
		
		case SelectPlaylistFile:
			if (playlistFileChooser.showSaveDialog(mainWindow)==JFileChooser.APPROVE_OPTION) {
				playlistFile = playlistFileChooser.getSelectedFile();
				playlistFileNameTextField.setText(playlistFile.getPath());
				writeBaseConfig();
			}
			break;
			
		case CreateETS2File:
			ProgressDialog.runWithProgressDialog(mainWindow, "Progress", 200, pd -> {
				enableGUI(false);
				createETS2fileTask(pd);
				enableGUI(true);
			});
			break;
			
		case CreatePlaylistFile:
			ProgressDialog.runWithProgressDialog(mainWindow, "Progress", 200, pd -> {
				enableGUI(false);
				createPlaylistFileTask(pd);
				enableGUI(true);
			});
			break;
			
		case ImportStationAdresses:
			ProgressDialog.runWithProgressDialog(mainWindow, "Progress", 200, pd -> {
				enableGUI(false);
				importStationAdressesTask(pd);
				enableGUI(true);
			} );
			break;
			
		case GotoETS2File:
			if (filemanagerPath!=null && ets2listFile!=null) {
				try {
					execute( filemanagerPath, ets2listFile.getParent() );
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
			break;
			
		case GotoPlaylistFile:
			if (filemanagerPath!=null && playlistFile!=null) {
				try {
					execute( filemanagerPath, playlistFile.getParent() );
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
			break;
			
		case EditConfigFiles:
			if (texteditorPath!=null)
				try {
					execute( texteditorPath, FILENAME_BASECONFIG,FILENAME_STATIONS_LIST );
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			break;
		}
	}

	private void execute(String... cmdarray) throws IOException {
		System.out.println("Call command: "+Arrays.toString(cmdarray));
		Runtime.getRuntime().exec(cmdarray);
	}

	private void importStationAdressesTask(ProgressDialog pd) {
		pd.setTaskTitle("Read station list:");
		pd.setIndeterminate(true);
		
		readStationList();
		for(Station station : stationList) {
			System.out.println("station: "+station);
		}
		
		pd.setTaskTitle("Import station adresses:");
		pd.setValue(0, stationList.size());
		adressList.clear();
		stationListTextArea.setText("");;
		//for (Station station: stationList) {
		for (int i=0; (i<stationList.size()) && !pd.wasCanceled(); i++) {
			Station station = stationList.get(i);
			Vector<StreamAdress> streamAdresses = station.readStreamAdressesFromWeb();
			if (streamAdresses!=null) {
				adressList.addAll(streamAdresses);
				System.out.println("station: "+station);
				stationListTextArea.append(String.format("station: %s\r\n", station.name));
				stationListTextArea.append(String.format("  list: %s\r\n", station.url));
				for (StreamAdress addr: streamAdresses) {
					System.out.println("\t"+addr);
					stationListTextArea.append(String.format("    %s\r\n", addr.url));
				}
			}
			pd.setValue(i+1);
		}
		if (pd.wasCanceled()) {
			adressList.clear();
			stationListTextArea.setText("");
		}
	}

	private void createPlaylistFileTask(ProgressDialog pd) {
		pd.setTaskTitle("Create playlist file:");
		String content = createPlaylist(pd,adressList);
		writeContentTo(content,playlistFile);
		playlistContentTextArea.setText(content);
	}

	private void createETS2fileTask(ProgressDialog pd) {
		pd.setTaskTitle("Create ETS2 file:");
		String content = createETS2streamlist(pd,adressList);
		writeContentTo(content,ets2listFile);
		ets2listContentTextArea.setText(content);
	}

	private void writeContentTo(String content, File outputFile) {
		PrintWriter output;
		try { output = new PrintWriter(outputFile); }
		catch (FileNotFoundException e) { e.printStackTrace(); return; }
		output.println(content);
		output.close();
	}

	private String createPlaylist(ProgressDialog pd, Vector<StreamAdress> adressList) {
		StringBuilder sb = new StringBuilder();
		sb.append("[playlist]").append("\r\n");
		sb.append("numberofentries=").append(adressList.size()).append("\r\n");
		for (int i=0; i<adressList.size(); i++) {
			StreamAdress adress = adressList.get(i);
			sb.append(String.format("File%d=%s\r\n", i+1,adress.url));
			sb.append(String.format("Title%d=%s\r\n", i+1,adress.name));
			sb.append(String.format("Length%d=-1\r\n", i+1));
		}
		sb.append("Version=2").append("\r\n");
		return sb.toString();
	}

	private String createETS2streamlist(ProgressDialog pd, Vector<StreamAdress> adressList) {
		StringBuilder sb = new StringBuilder();
		sb.append("SiiNunit\r\n");
		sb.append("{\r\n");
		sb.append("live_stream_def : _nameless.35BF.92E8 {\r\n");
		for (int i=0; i<adressList.size(); i++) {
			StreamAdress adress = adressList.get(i);
			//sb.append(String.format("stream_data[]: \"%s|%s\"\r\n", adress.url,adress.name));
			sb.append(String.format("stream_data[]: \"%s|%s|%s|%s|%d|%d\"\r\n", adress.url,adress.name,adress.genre,adress.country,adress.bitRate,adress.isFavorite));
			//stream_data[32]: "http://striiming.trio.ee/uuno.mp3|Raadio Uuno|Rock|EST|128|0"
		}
		sb.append("}\r\n");
		sb.append("}\r\n");
		return sb.toString();
	}

	private void readStationList() {
		BufferedReader input;
		try { input = new BufferedReader( new FileReader(FILENAME_STATIONS_LIST) ); } catch (FileNotFoundException e) { return; }
		String str;
		Station station = null;
		stationList.clear();
		try {
			while( (str=input.readLine())!=null ) {
				if (str.toLowerCase().equals("[station]")) { station = new Station(); stationList.add(station); continue; }
				if (station!=null) {
					if (str.startsWith("url=" )) { station.set("url" , str.substring("url=" .length())); continue; } // processStationListLine(str, "url" );
					if (str.startsWith("name=")) { station.set("name", str.substring("name=".length())); continue; } // processStationListLine(str, "name");
					if (str.startsWith("type=")) { station.set("type", str.substring("type=".length())); continue; } // processStationListLine(str, "type");
				}
			}
		} catch (IOException e1) {}
		try { input.close(); } catch (IOException e) {}
	}

	private void readBaseConfig() {
		BufferedReader input;
		try { input = new BufferedReader( new FileReader(FILENAME_BASECONFIG) ); } catch (FileNotFoundException e) { return; }
		String str;
		try {
			while( (str=input.readLine())!=null ) {
				if (str.startsWith("ets2listFile=")) { ets2listFile    = new File(str.substring("ets2listFile=".length())); System.out.println("Found predefined ets2list file in config: \""+ets2listFile.getPath()+"\""); ets2listFileNameTextField.setText(ets2listFile.getPath()); ets2listFileChooser.setSelectedFile(ets2listFile); }
				if (str.startsWith("playlistFile=")) { playlistFile    = new File(str.substring("playlistFile=".length())); System.out.println("Found predefined playlist file in config: \""+playlistFile.getPath()+"\""); playlistFileNameTextField.setText(playlistFile.getPath()); playlistFileChooser.setSelectedFile(playlistFile); }
				if (str.startsWith("texteditor="  )) { texteditorPath  = str.substring("texteditor=" .length()); System.out.println("Found path to text editor in config: \""+texteditorPath+"\""); }
				if (str.startsWith("filemanager=" )) { filemanagerPath = str.substring("filemanager=".length()); System.out.println("Found path to filemanager in config: \""+filemanagerPath+"\""); }
			}
		} catch (IOException e1) {}
		try { input.close(); } catch (IOException e) {}
	}

	private void writeBaseConfig() {
		PrintWriter output;
		try { output = new PrintWriter(FILENAME_BASECONFIG); }
		catch (FileNotFoundException e) { e.printStackTrace(); return; }
		if (ets2listFile   !=null) output.println("ets2listFile="+ets2listFile.getPath());
		if (playlistFile   !=null) output.println("playlistFile="+playlistFile.getPath());
		if (texteditorPath !=null) output.println("texteditor="  +texteditorPath );
		if (filemanagerPath!=null) output.println("filemanager=" +filemanagerPath);
		output.close();
	}
	
	private static class StreamAdress {
		
		public String url;
		public String name;
		public String genre;
		public String country;
		public int bitRate;
		public int isFavorite;

		public StreamAdress(String name, String url) {
			this.url = url;
			this.name = name;
			this.genre = "";
			this.country = "";
			this.bitRate = 0;
			this.isFavorite = 1;
		}

		@Override
		public String toString() {
			return "StreamAdress [ name=\"" + name + "\", url=\"" + url + "\" ]";
		}
		
	}
	
	private static class Station {
		
		private String url;
		private String name;
		private String type;

		public Station() {
			url = null;
			name = null;
			type = null;
		}

		public void set(String field, String value) {
			if (field.equals("url" )) { this.url  = value; return; }
			if (field.equals("name")) { this.name = value; return; }
			if (field.equals("type")) { this.type = value; return; }
			throw new IllegalArgumentException("Unknown field: \""+field+"\" (value:"+value+")");
		}

		@Override
		public String toString() {
			return "Station [ name=\"" + name + "\", url=\"" + url + "\", type=\"" + type + "\" ]";
		}
		
		public Vector<StreamAdress> readStreamAdressesFromWeb() {
			if (url==null) return null;
			String content = getContent(url);
			if (content==null) return null;
			
			BufferedReader input = new BufferedReader( new StringReader(content) );
			Vector<StreamAdress> adresses = new Vector<>();
			String str;
			try {
				while( (str=input.readLine())!=null ) {
					if ("plain".equals(type)) {
						if (isURL(str))
							adresses.add(
								new StreamAdress(
									String.format("%s(%d)",name,adresses.size()+1),
									str
								)
							);
					} else if ("pls".equals(type)) {
						if (str.startsWith("File")) {
							int pos = str.indexOf('=');
							if (pos>=0)
								adresses.add(
									new StreamAdress(
										String.format("%s(%d)",name,adresses.size()+1),
										str.substring(pos+1)
									)
								);
						}
					}
				}
			} catch (IOException e1) {}
			
			try { input.close(); } catch (IOException e) {}
			
			if (adresses.size()==1) adresses.get(0).name = name;
			return adresses;
		}

		private boolean isURL(String url) {
			try {
				new URI(url).toURL();
				return true;
			}
			catch (URISyntaxException e) { return false; }
			catch (MalformedURLException e) { return false; }
		}

		private static String getContent(String url) {
			Object obj; 
			try { obj = new URI(url).toURL().getContent(); }
			catch (URISyntaxException e) { e.printStackTrace(); return null; }
			catch (MalformedURLException e) { e.printStackTrace(); return null; }
			catch (IOException e) { e.printStackTrace(); return null; }
			
			if (obj==null) return null;
			if (obj instanceof String) return (String)obj;
			if (obj instanceof InputStream) {
				StringBuilder sb = new StringBuilder();
				byte[] buffer = new byte[10000];
				InputStream input = (InputStream)obj;
				try {
					int len;
					while( (len=input.read(buffer))>=0 )
						sb.append(new String(buffer,0,len));
				} catch (IOException e) {}
				try { input.close(); } catch (IOException e) {}
				return sb.toString();
			}
			System.out.println("Unknown content type of station list: "+obj.getClass().getCanonicalName());
			return null;
		}
	}
}
