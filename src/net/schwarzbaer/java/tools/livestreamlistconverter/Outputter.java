package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.awt.BorderLayout;
import java.awt.Window;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;

import net.schwarzbaer.java.lib.gui.FileChooser;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;

class Outputter
{
	interface ExternalIF
	{
		Vector<StreamAdress> getAdressList();
		void enableGUI(boolean enable);
	}
	
	private final ExternalIF externalIF;
	private final BaseConfig baseConfig;
	        final OutputFormat outputFormat;
	private final FileChooser fileChooser;
	private Panel panel;
	private File outputFile;

	Outputter(BaseConfig baseConfig, OutputFormat outputFormat, ExternalIF externalIF)
	{
		this.externalIF    = Objects.requireNonNull(externalIF);
		this.baseConfig    = Objects.requireNonNull(baseConfig   );
		this.outputFormat  = Objects.requireNonNull(outputFormat );
		fileChooser = new FileChooser(
				this.outputFormat.fileTypeName,
				this.outputFormat.fileTypeExt
		);
		
		panel = null;
		outputFile = null;
	}
	
	File getOutputFile()
	{
		return outputFile;
	}

	void setOutputFile(File outputFile)
	{
		this.outputFile = outputFile;
		fileChooser.setSelectedFile(this.outputFile);
		if (panel!=null)
		{
			panel.fileField.setText(this.outputFile==null ? "" : this.outputFile.getAbsolutePath());
			panel.setEnabled(true);
		}
	}
	
	void generateAndWriteContentToFile(ProgressDialog pd) {
		Vector<StreamAdress> adressList = externalIF.getAdressList();
		pd.setTaskTitle( "Create %s:".formatted( outputFormat.fileLabel ) );
		String content = outputFormat.createOutputFileContent(pd,adressList);
		if (outputFile!=null)
			writeContentTo( content, outputFile );
		if (panel!=null)
			panel.contentTextArea.setText(content);
	}

	private static void writeContentTo(String content, File outputFile) {
		try (PrintWriter output = new PrintWriter(outputFile))
		{
			output.println(content);
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
	}
	
	Panel createPanel(Window parent)
	{
		return panel = new Panel(parent);
	}
	
	void setPanelEnabled(boolean enable)
	{
		if (panel!=null)
			panel.setEnabled(enable);
	}

	private class Panel extends JPanel
	{
		private static final long serialVersionUID = 2132641733100889592L;
		
		private final JTextField fileField;
		private final JTextArea contentTextArea;
		private final JButton btnSetOutputFile;
		private final JButton btnOpenFolder;
		private final JButton btnWriteContentToFile;
		
		Panel(Window parent)
		{
			super(new BorderLayout(2,2));
			setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
			
			fileField = new JTextField( outputFile==null ? "" : outputFile.getAbsolutePath() );
			fileField.setEditable(false);
			
			contentTextArea = new JTextArea();
			contentTextArea.setEditable(false);
			JScrollPane contentTextAreaScrollPane = new JScrollPane(contentTextArea);
			
			JToolBar toolBar = new JToolBar();
			toolBar.setFloatable(false);
			
			toolBar.add(btnSetOutputFile = LiveStreamListConverter.createButton("Set Output File", e->{
				if (fileChooser.showSaveDialog(parent)==JFileChooser.APPROVE_OPTION) {
					outputFile = fileChooser.getSelectedFile();
					fileField.setText( outputFile.getAbsolutePath() );
					baseConfig.writeToFile();
				}
			}));
			
			toolBar.add(btnOpenFolder = LiveStreamListConverter.createButton("Open Folder", GrayCommandIcons.IconGroup.Folder, e->{
				if (baseConfig.filemanagerPath!=null && outputFile!=null) {
					try {
						LiveStreamListConverter.execute( baseConfig.filemanagerPath, outputFile.getParent() );
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			}));
			
			toolBar.add(btnWriteContentToFile = LiveStreamListConverter.createButton("Generate & Write Content to File", GrayCommandIcons.IconGroup.Save, e->{
				ProgressDialog.runWithProgressDialog(parent, "Progress", 200, pd -> {
					externalIF.enableGUI(false);
					generateAndWriteContentToFile(pd);
					externalIF.enableGUI(true);
				});
			}));

			JPanel centerPanel = new JPanel(new BorderLayout(2,2));
			centerPanel.add(fileField, BorderLayout.NORTH);
			centerPanel.add(contentTextAreaScrollPane, BorderLayout.CENTER);
			
			add(toolBar, BorderLayout.PAGE_START);
			add(centerPanel, BorderLayout.CENTER);
			updateGuiAccess();
		}
		
		void updateGuiAccess()
		{
			setEnabled(isEnabled());
		}
		
		@Override
		public void setEnabled(boolean enabled)
		{
			super.setEnabled(enabled);
			btnSetOutputFile     .setEnabled(enabled);
			btnOpenFolder        .setEnabled(enabled && baseConfig.filemanagerPath!=null && outputFile!=null);
			btnWriteContentToFile.setEnabled(enabled);
		}
		
		
	}
}
