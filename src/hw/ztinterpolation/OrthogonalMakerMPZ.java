package hw.ztinterpolation;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.Calibration;
import ij.plugin.MontageMaker;
import ij.plugin.filter.GaussianBlur;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.function.Function;


//20141126 xz,yz画像を作るためのクラス
//20150709 MakePseudoZsliceに転用
//20161109 公開、java1.8, streamを使用するにあたり大幅変更。不要部分の削除、swingworker不使用へ
//　コード自体も見直す。

public class OrthogonalMakerMPZ {
	ArrayList<ImagePlus> original_imps = null;
	int p_interval = 1;
	boolean message = false;
	static GaussianBlur gb_filter = new GaussianBlur();
	
	MontageMaker mm = new MontageMaker();					

	int height;
	int width;
	int tSize;
	int cSize;
	int zSize;
	int numOfHeight;
	static int bitDepth;

	double zxRatio;
	double magnification;
	double gsd;
	
	public OrthogonalMakerMPZ(ArrayList<ImagePlus> ips){
		original_imps = ips;
		height = original_imps.get(0).getHeight();
		width = original_imps.get(0).getWidth();
		cSize = original_imps.get(0).getNChannels();
		zSize = original_imps.get(0).getNSlices();
		tSize = original_imps.get(0).getNFrames();
		numOfHeight = original_imps.size();
		bitDepth = original_imps.get(0).getBitDepth();
		
		Calibration cal = original_imps.get(0).getCalibration();
		double calx = cal.pixelWidth;
		double calz = cal.pixelDepth;;
		zxRatio = calz/calx;
		//magnification = (int)Math.floor(zxRatio); //このthicknessは可変になる予定
		magnification = zxRatio; //doubleに変更 2019.07.08

		
	}
	
	public void setMessage(boolean op){
		message = op;
	}
	
	public void setInterval(int interval){
		p_interval = interval;
	}

	public void setMagnification(double m){ //整数倍で -> doubleに変更 20190708
		magnification = m;
	}
	
	public void setGsd(double g){
		gsd = g;
	}
	
	public ImagePlus makeXZ(int n){
		ImagePlus xzImage = new ImagePlus();


		xzImage = makeXZimage(original_imps.get(n), magnification, gsd);
		
		return xzImage;
		
	}


	public static ImagePlus makeXZimage(ImagePlus im, double magnification, double gsd){
		MontageMaker mm = new MontageMaker();					

		ImagePlus xzImage = new ImagePlus();

		int height = im.getHeight();
		int width = im.getWidth();
		int cSize = im.getNChannels();
		int zSize = im.getNSlices();
		int tSize= im.getNFrames();
		
		
		int colums = 1;
		int rows = zSize;
		double scale = 1.0;
		int first = 1;
		int last = zSize;
		int inc = 1; //increment
		int borderWidth = 0;
		boolean labels = false;
		
		ImageStack resultStack = new ImageStack(width, (int)(zSize * magnification));
		
		for(int t = 0; t < tSize; t++){
			
			for(int c = 0; c < cSize; c++){
				ImagePlus buffZimg = new ImagePlus();
				ImageStack buffStack = new ImageStack(width, height);
		
				for(int z = 0; z < zSize; z++){
					int index = im.getStackIndex(c+1, z+1, t+1);
					buffStack.addSlice(im.getStack().getProcessor(index));
				}
				buffZimg = new ImagePlus("",buffStack);
				//buffZimg.show();
				ImagePlus mm_img = mm.makeMontage2(buffZimg,colums,rows,scale,first,last,inc,borderWidth,labels);
				//ImagePlus mm_img = mm.makeMontage2(buffZimg,1,15,1,1,15,1,0,false);
				
				ImageProcessor resizeP = resize(mm_img, width, zSize, magnification, gsd);
				resultStack.addSlice(resizeP);					

			}
		}
		xzImage = new ImagePlus("", resultStack);
		xzImage.setDimensions(cSize, 1, tSize);
		
		return xzImage;
	}
	
	
	public ImagePlus[] makeXZimage(){
		
		// クラスを並列でたくさん作るとカーネルに貯まるのかも。
		// そこでImagePlusの配列自体を並列処理するようにしてみる。 20161125
		// なんと、eclipseのデバッグモードでのスレッド数の上限があるようで。。。2048個になるとcan not create native threadとでる。
		
		int colums = 1;
		int rows = zSize;
		double scale = 1.0;
		int first = 1;
		int last = zSize;
		int inc = 1; //increment
		int borderWidth = 0;
		boolean labels = false;
		
		
		ImagePlus[] xzImages = new ImagePlus[numOfHeight];
		
		ArrayList<Integer> count = new ArrayList<Integer>(numOfHeight);

		xzImages = original_imps.parallelStream().map((Function<ImagePlus, ImagePlus>) buffimp ->{
			ImageStack resultStack = new ImageStack(width, (int)(zSize * magnification));
			ImagePlus resultImg = new ImagePlus();
			
			count.add(1);
			double d = (double)count.size();
			IJ.showStatus(String.valueOf("Now Making...." + Math.round((d/numOfHeight) * 100)) + "%");


			for(int t = 0; t < tSize; t++){
				
				for(int c = 0; c < cSize; c++){
					ImagePlus buffZimg = new ImagePlus();
					ImageStack buffStack = new ImageStack(width, height);
			
					for(int z = 0; z < zSize; z++){
						int index = buffimp.getStackIndex(c+1, z+1, t+1);

						buffStack.addSlice(buffimp.getStack().getProcessor(index));
					}
					buffZimg = new ImagePlus("",buffStack);
					ImagePlus mm_img = mm.makeMontage2(buffZimg,colums,rows,scale,first,last,inc,borderWidth,labels);

					ImageProcessor resizeP = resize(mm_img, width, zSize, magnification, magnification);
					resultStack.addSlice(resizeP);

				}
			}
			resultImg = new ImagePlus("", resultStack);
			resultImg.setDimensions(cSize, 1, tSize);
			return resultImg;
		} ).toArray(i -> new ImagePlus[i]);
		
		
		return xzImages;
	}
	
	
	
	
	public static ImageProcessor resize(ImagePlus imp, int width, int height, double thickness, double gsd){
		//ImageProcessor return_ip = null;
		//やっぱりbandpass filterは遅い。。。
		
		ImageProcessor pi_ip = imp.getProcessor();
		//ImageProcessor.setUseBicubic(true);
		ImageProcessor resized_p = pi_ip.resize(width, (int)(height * thickness), true);
		gb_filter.blurGaussian(resized_p, 1, gsd, 0.02); // 拡張倍率によって変更するべきでは？


		return resized_p;

		/* "T"において不具合が出る可能性あり(うごいたけど)ちょっと保留 2023.3.30 黒抜けアーティファクトが出る。 gsd値の変更でうまくいくのか？->小さくすると減る(4倍のとき0.3くらい)
		/*ここで行うと、単純にもとのモザイク画像に戻る傾向が見える。もっと工夫がいるかも。
		//この場合(*0.3)、Zの処理に不具合が出そう。
		LucyRichardson lucyRichardson = new LucyRichardson(resized_p);
		lucyRichardson.setGaussianBlurProperties(1, gsd*0.3, 0.02);
		ImageProcessor deconvolutionImage = lucyRichardson.getProcessedImage(0, 10);

		deconvolutionImage.medianFilter();

		switch (bitDepth){
			case 8:
				deconvolutionImage = deconvolutionImage.convertToByteProcessor(false);
				break;
			case 16:
				deconvolutionImage = deconvolutionImage.convertToShortProcessor(false);
				break;
		}

		return deconvolutionImage;

		*/
	}
	
	
}