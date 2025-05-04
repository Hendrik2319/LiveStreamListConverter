package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.Vector;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;

import net.schwarzbaer.java.lib.gui.FileChooser;
import net.schwarzbaer.java.lib.gui.GeneralIcons.GrayCommandIcons;
import net.schwarzbaer.java.lib.gui.ProgressDialog;

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
	private       Panel panel;
	private final Vector<File> outputFiles;
	private       Runnable doBeforeGenerating;

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
		outputFiles = new Vector<>();
		doBeforeGenerating = null;
	}
	
	void forEachOutputFile(Consumer<File> action)
	{
		outputFiles.forEach(action);
	}
	
	void addOutputFile(File outputFile)
	{
		if (outputFile==null) return;
		outputFiles.add(outputFile);
		fileChooser.setSelectedFile(outputFile);
		if (panel!=null)
		{
			panel.updateFileFields();
			panel.setEnabled(true);
		}
	}
	
	void generateAndWriteContentToFile(ProgressDialog pd) {
		if (doBeforeGenerating!=null)
			doBeforeGenerating.run();
		Vector<StreamAdress> adressList = externalIF.getAdressList();
		SwingUtilities.invokeLater(()->{
			pd.setTaskTitle( "Create %s:".formatted( outputFormat.fileLabel ) );
			pd.setValue(0, adressList.size());
		});
		String content = outputFormat.createOutputFileContent(adressList, progress -> SwingUtilities.invokeLater( () -> pd.setValue(progress) ));
		outputFiles.forEach( outputFile -> writeContentTo( content, outputFile ) );
		if (panel!=null)
			SwingUtilities.invokeLater(()->{
				panel.contentTextArea.setText(content);
			});
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
	
	Panel createPanel(Window parent, Runnable doBeforeGenerating)
	{
		this.doBeforeGenerating = doBeforeGenerating;
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
		
		private final JPanel fileFieldsPanel;
		private final JTextArea contentTextArea;
		private final JButton btnAddOutputFile;
		private final Vector<JButton> btnArrOpenFolder;
		private final Vector<JButton> btnArrRemoveFile;
		private final JButton btnWriteContentToFile;
		
		Panel(Window parent)
		{
			super(new BorderLayout(2,2));
			setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
			
			contentTextArea = new JTextArea();
			contentTextArea.setEditable(false);
			JScrollPane contentTextAreaScrollPane = new JScrollPane(contentTextArea);
			
			JToolBar toolBar = new JToolBar();
			toolBar.setFloatable(false);
			
			toolBar.add(btnAddOutputFile = LiveStreamListConverter.createButton("Add Output File", GrayCommandIcons.IconGroup.Add, e->{
				if (fileChooser.showSaveDialog(parent)==JFileChooser.APPROVE_OPTION) {
					File outputFile = fileChooser.getSelectedFile();
					outputFiles.add(outputFile);
					updateFileFields();
					baseConfig.writeToFile();
				}
			}));
			
			toolBar.add(btnWriteContentToFile = LiveStreamListConverter.createButton("Generate & Write Content to File", GrayCommandIcons.IconGroup.Save, e->{
				ProgressDialog.runWithProgressDialog(parent, "Progress", 200, pd -> {
					externalIF.enableGUI(false);
					generateAndWriteContentToFile(pd);
					externalIF.enableGUI(true);
				});
			}));
			
			btnArrOpenFolder = new Vector<>();
			btnArrRemoveFile = new Vector<>();
			fileFieldsPanel = new JPanel(new GridBagLayout());

			JPanel centerPanel = new JPanel(new BorderLayout(2,2));
			centerPanel.add(fileFieldsPanel, BorderLayout.NORTH);
			centerPanel.add(contentTextAreaScrollPane, BorderLayout.CENTER);
			
			add(toolBar, BorderLayout.PAGE_START);
			add(centerPanel, BorderLayout.CENTER);
			
			updateFileFields();
		}
		
		void openFolder(File outputFile) {
			if (baseConfig.filemanagerPath!=null && outputFile!=null) {
				try {
					LiveStreamListConverter.execute( baseConfig.filemanagerPath, outputFile.getParent() );
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
		
		void removeFile(File outputFile)
		{
			outputFiles.remove(outputFile);
			updateFileFields();
			baseConfig.writeToFile();
		}

		void updateFileFields()
		{
			fileFieldsPanel.removeAll();
			btnArrOpenFolder.clear();
			btnArrRemoveFile.clear();
			
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.BOTH;
			c.gridwidth = 1;
			c.gridheight = 1;
			c.weightx = 0;
			c.weighty = 0;
			c.gridy = -1;
			
			outputFiles.forEach( outputFile -> {
				JTextField fileField = new JTextField( outputFile.getAbsolutePath() );
				fileField.setEditable(false);
				
				JButton openFolderBtn = LiveStreamListConverter.createButton("Open Folder", GrayCommandIcons.IconGroup.Folder, e -> openFolder(outputFile));
				JButton removeFileBtn = LiveStreamListConverter.createButton("Remove File", GrayCommandIcons.IconGroup.Delete, e -> removeFile(outputFile));
				btnArrOpenFolder.add(openFolderBtn);
				btnArrRemoveFile.add(removeFileBtn);
				
				c.gridy++;
				c.gridx = -1;
				c.gridx++; c.weightx = 1; fileFieldsPanel.add(fileField, c);
				c.gridx++; c.weightx = 0; fileFieldsPanel.add(openFolderBtn, c);
				c.gridx++; c.weightx = 0; fileFieldsPanel.add(removeFileBtn, c);
			} );
			revalidate();
			repaint();
			setEnabled(isEnabled());
		}
		
		@Override
		public void setEnabled(boolean enabled)
		{
			super.setEnabled(enabled);
			btnAddOutputFile     .setEnabled(enabled);
			btnArrOpenFolder.forEach( btn -> btn.setEnabled(enabled && baseConfig.filemanagerPath!=null) );
			btnArrRemoveFile.forEach( btn -> btn.setEnabled(enabled) );
			btnWriteContentToFile.setEnabled(enabled && !outputFiles.isEmpty());
		}
	}
}
