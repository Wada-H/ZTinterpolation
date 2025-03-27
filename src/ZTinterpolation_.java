import hw.ztinterpolation.ZTinterpolation;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import ij.plugin.frame.PlugInFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;



/********************************************************/
/*     z-projection画像より擬似 sliceを創作するプラグイン      */
/********************************************************/

/*
ZTinterpolation_.java

概要

Orthgonal view(OrthgonalMakerMPZ.java)画像を再構築してxy画像を作る。



更新履歴
20150708 MakePseudoZslice_ project start
20150710 とりあえず、動くものができた。
	orthogonal 画象より再構築する際、SwingWorkerにもかかわらず、シングルスレッドで動く、、、
	 ->なんとかマルチで動かせないものか？

	初めはZのみで考えていたが、Tについてもそれなりの画像ができることが判明。
	プロジェクト名をZTinterpolation_に変更
 
20161109 公開に向けて見直し開始
 	PluginのタイトルがMakePseudoZslice -> ZTinterpolationに変更

20161201 表示と処理の分離を行う。->完了

20170221 元画像の情報を新しい画像にも拡張分を考慮して反映させる ->完了

20190405 ImageJ 1.52n以降におけるduplicate()の仕様変更に対する修正

20190708 倍率をdoubleに変更

20230210 LucyRichardson導入試験。Zは問題なく動きそうだが、Tに関してNullPointerExceptionが出る。

20230419 とりあえず、deconvolutionの数値決め打ち、resize機能追加(並列化したもの)を導入。
	-> いずれ数値を任意に入れるためのGUIを用意する必要がある。これにはJavaFxで書き直すほうがいいかもしれない。

@author    Housei Wada

*/


public class ZTinterpolation_ extends PlugInFrame implements MouseListener, MouseMotionListener, WindowListener{

	// 表示画像の情報 //
	
	
	ImagePlus imp;

	int z;
	int t;
	


	double pixelWidth;
	double pixelDepth;
	
	// interpolation 画象に必要

	double pixel_micron;
	double micron_slice;
	
	
	// plugin panel //
	
	JButton makeButton;
	
	JTextField pixel_micron_Text;
	JLabel pixel_micron_Label;

	JTextField micron_slice_Text;
	JLabel micron_slice_Label;

	JTextField magnification_Text;
	JLabel magnification_Label;
	
	JRadioButton t_Radio;
	JRadioButton z_Radio;
	ButtonGroup t_or_z_Radio;

	JLabel scaleFactorLabel;
	JCheckBox scaleFactorCB;
	JTextField scaleFactorText;

	JLabel deconvoLabel;
	JCheckBox deconvoCB;

	// new image //
	
	ImagePlus[] img_array;
	ImagePlus newImage;
	
	
	public ZTinterpolation_() {
		super("ZTinterpolation ver.20230419");

		if(readCurrentImageInfo()){
			showPanel();
		}
	}

	
	public boolean readCurrentImageInfo(){
		imp = WindowManager.getCurrentImage();

		if (imp == null) {
			IJ.noImage();
			return false;
		}
				
		Calibration cal = imp.getCalibration();
		

		z = imp.getNSlices();
		t = imp.getNFrames();

		
		pixelWidth = cal.pixelWidth;
		pixelDepth = cal.pixelDepth;
		
		pixel_micron = 1.0 / pixelWidth;
		micron_slice = pixelDepth;
		
		if((t == 1)&&(z == 1)){
			IJ.showMessage("This image do not have Z or T slices.");
			//return false;
		}
		
		return true;
		
		
	}
	
	
	public void showPanel(){
        FlowLayout gd_layout = new FlowLayout();
    	//gd_layout.setAlignOnBaseline(true);
    	gd_layout.setAlignment(FlowLayout.CENTER);
        
        JPanel gd_panel = new JPanel(gd_layout);
    	gd_panel.setPreferredSize(new Dimension(400, 280));
    	
    	JPanel mainPanel = new JPanel((new GridLayout(9,2)));
    	mainPanel.setAlignmentX(Component.BOTTOM_ALIGNMENT);
    	
    	z_Radio = new JRadioButton("Z", true);
    	z_Radio.addMouseListener(this);
    	
    	t_Radio = new JRadioButton("T", false);
    	t_Radio.addMouseListener(this);
    	
    	t_or_z_Radio = new ButtonGroup();
    	t_or_z_Radio.add(z_Radio);
    	t_or_z_Radio.add(t_Radio);

    	pixel_micron_Text = new JTextField(String.valueOf(pixel_micron));
    	pixel_micron_Label = new JLabel("pixel/micron");
    	
    	micron_slice_Text = new JTextField(String.valueOf(micron_slice));
    	micron_slice_Label = new JLabel("micron/slice");

    	magnification_Text = new JTextField("4");
    	magnification_Label = new JLabel("x");
    	magnification_Text.setEnabled(false);

		scaleFactorLabel = new JLabel("Scaling factor");
		scaleFactorCB = new JCheckBox();
		scaleFactorCB.setSelected(false );
		scaleFactorText = new JTextField("2");

    	deconvoLabel = new JLabel("Deconvolution");
    	deconvoCB = new JCheckBox();
    	deconvoCB.setSelected(true);
    	
    	makeButton = new JButton("Make");
    	makeButton.addMouseListener(this);
 
    	JLabel blank_label1 = new JLabel("");
    	JLabel blank_label2 = new JLabel("");
    	JLabel blank_label3 = new JLabel("");
		JLabel blank_label4 = new JLabel("");

    	mainPanel.add(z_Radio);
    	mainPanel.add(blank_label1);

    	mainPanel.add(pixel_micron_Text);
    	mainPanel.add(pixel_micron_Label);

    	mainPanel.add(micron_slice_Text);
    	mainPanel.add(micron_slice_Label);
    	
    	mainPanel.add(t_Radio);
    	mainPanel.add(blank_label2);

    	mainPanel.add(magnification_Text);
    	mainPanel.add(magnification_Label);

    	mainPanel.add(scaleFactorCB);
		mainPanel.add(blank_label4);

		mainPanel.add(scaleFactorLabel);
    	mainPanel.add(scaleFactorText);

    	mainPanel.add(deconvoLabel);
    	mainPanel.add(deconvoCB);

    	mainPanel.add(blank_label3);
    	mainPanel.add(makeButton);
    	
    	
    	gd_panel.add(mainPanel);
    	
    	this.add(gd_panel);
		this.pack(); //推奨サイズのｗindow
		
		Point imp_point = imp.getWindow().getLocation();
		int imp_window_width = imp.getWindow().getWidth();

		double set_x_point = imp_point.getX() + imp_window_width;
		double set_y_point = imp_point.getY();
		
		this.setLocation((int)set_x_point, (int)set_y_point);

		this.setVisible(true);//thisの表示

		this.addWindowListener(this);
    	imp.getWindow().addWindowListener(this);
    	
    	
    	if(z < 2){
    		z_Radio.setEnabled(false);
    		pixel_micron_Text.setEnabled(false);
    		micron_slice_Text.setEnabled(false);

    		t_Radio.setSelected(true);
    		magnification_Text.setEnabled(true);
    		
    	}else if(t < 2){
    		t_Radio.setEnabled(false);
    	}
    	
    	
	}
	
	
	
	public void makePseudoSlice() {
		
		ZTinterpolation zti = new ZTinterpolation(imp);
		ImagePlus pseudoImage = new ImagePlus();


		boolean magValue = scaleFactorCB.isSelected();
		double scaleFactor = Double.valueOf(scaleFactorText.getText());
		zti.setCheckMagnification(magValue, scaleFactor);

		boolean deconvo = deconvoCB.isSelected();
		zti.setCheckDeconvolution(deconvo);

		if(z_Radio.isSelected()){
			pixel_micron = Double.valueOf(pixel_micron_Text.getText());
			micron_slice = Double.valueOf(micron_slice_Text.getText());

			double zScalingFactor = pixel_micron * micron_slice;

			
			//zti.makeZinterpolation((int)Math.floor(zScalingFactor));
			zti.makeZinterpolation(zScalingFactor);


		}else if(t_Radio.isSelected()){
			double tScalingFactor = Double.valueOf(magnification_Text.getText());
			
			zti.makeTinterpolation(tScalingFactor);

		}
		try {
			zti.close();
			System.gc();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println("don't close");
			
		}
		pseudoImage.show();
	}
		
	
	
	@Override
	public void mouseDragged(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseMoved(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseClicked(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseEntered(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseExited(MouseEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mousePressed(MouseEvent arg0) {
		// TODO Auto-generated method stub

		if(arg0.getSource() == z_Radio){
			if(z > 1){
				magnification_Text.setEnabled(false);
				pixel_micron_Text.setEnabled(true);
				micron_slice_Text.setEnabled(true);
			}
			
		}else if(arg0.getSource() == t_Radio){
			if(t > 1){
				magnification_Text.setEnabled(true);
				pixel_micron_Text.setEnabled(false);
				micron_slice_Text.setEnabled(false);
			}
		}
	}

	@Override
	public void mouseReleased(MouseEvent arg0) {
		// TODO Auto-generated method stub

		if(arg0.getSource() == makeButton){
			makePseudoSlice();

		}
	}  

	@Override
	public void windowClosed(WindowEvent arg0) {
		// TODO Auto-generated method stub
		System.out.println("closed");
	}

	@Override
	public void windowClosing(WindowEvent arg0) {

		if(arg0.getSource() == this){
			this.close();
		}else if(arg0.getSource() == imp.getWindow()){
			this.close();
		}
	}
	
	
}

