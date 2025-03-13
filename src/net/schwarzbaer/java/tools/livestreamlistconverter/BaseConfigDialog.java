package net.schwarzbaer.java.tools.livestreamlistconverter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.io.File;
import java.util.function.Consumer;

import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import net.schwarzbaer.java.lib.gui.FileChooser;
import net.schwarzbaer.java.lib.gui.StandardDialog;

class BaseConfigDialog extends StandardDialog
{
	private static final long serialVersionUID = -1376057487558163729L;
	
	private final BaseConfig baseConfig;

	private final FileChooser executableFileChooser;
	private final JTextArea configOutput;

	private BaseConfigDialog(Window parent, BaseConfig baseConfig)
	{
		super(parent, "Configuration", ModalityType.APPLICATION_MODAL, false);
		this.baseConfig = baseConfig;
		
		executableFileChooser = new FileChooser("Executable", "exe");
		
		configOutput = new JTextArea();
		configOutput.setEditable(false);
		JScrollPane configOutputScrollPane = new JScrollPane(configOutput);
		configOutputScrollPane.setPreferredSize(new Dimension(600,150));
		
		JPanel buttonsPanel = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.weightx = 1;
		c.weighty = 0;
		c.gridx = 0;
		c.gridy = -1;
		c.anchor = GridBagConstraints.WEST;
		
		c.gridy++;
		buttonsPanel.add(LiveStreamListConverter.createButton("Set TextEditor" , e -> selectExecutable(
				"TextEditor",
				this.baseConfig.texteditorPath ,
				file -> this.baseConfig.texteditorPath  = file.getAbsolutePath()
		)), c);
		
		c.gridy++;
		buttonsPanel.add(LiveStreamListConverter.createButton("Set FileManager", e -> selectExecutable(
				"FileManager",
				this.baseConfig.filemanagerPath,
				file -> this.baseConfig.filemanagerPath = file.getAbsolutePath()
		)), c);
		
		c.weighty = 1;
		c.gridy++;
		buttonsPanel.add(new JLabel(), c);
		
		JPanel contentPane = new JPanel(new BorderLayout());
		contentPane.add(buttonsPanel, BorderLayout.WEST);
		contentPane.add(configOutputScrollPane, BorderLayout.CENTER);
		
		createGUI(contentPane, LiveStreamListConverter.createButton("Close", e->closeDialog()));
	}

	private void selectExecutable(String targetName, String prevPath, Consumer<File> setSelectedFile)
	{
		if (prevPath!=null)
			executableFileChooser.setSelectedFile(new File(prevPath));
		
		executableFileChooser.setDialogTitle("Select path to %s".formatted(targetName));
		
		if (executableFileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			setSelectedFile.accept( executableFileChooser.getSelectedFile() );
			baseConfig.writeToFile();
			updateConfigOutput();
		}
	}
	
	private void updateConfigOutput()
	{
		StringBuilder sb = new StringBuilder();
		
		sb.append("Path to TextEditor :\r\n");
		if (baseConfig.texteditorPath==null)
			sb.append("    %s%n".formatted("<undefined>"));
		else
			sb.append("    %s%n".formatted(baseConfig.texteditorPath));
		
		sb.append("\r\nPath to FileManager :\r\n");
		if (baseConfig.filemanagerPath==null)
			sb.append("    %s%n".formatted("<undefined>"));
		else
			sb.append("    %s%n".formatted(baseConfig.filemanagerPath));
		
		configOutput.setText(sb.toString());
	}

	static void showDialog(Window parent, BaseConfig baseConfig)
	{
		BaseConfigDialog dlg = new BaseConfigDialog(parent, baseConfig);
		dlg.updateConfigOutput();
		dlg.showDialog();
	}
}
