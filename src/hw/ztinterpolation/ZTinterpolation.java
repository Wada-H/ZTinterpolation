package hw.ztinterpolation;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.HyperStackConverter;
import ij.process.ImageProcessor;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class ZTinterpolation implements AutoCloseable {
	
	// 表示画像の情報 //
	
		ImagePlus imp;
		int width;
		int height;
		int c;
		int z;
		int t;
		int b;
		
		FileInfo fi;
		Calibration cal;

		double pixelWidth;
		double pixelHeight;
		double pixelDepth;
		
		// interpolation 画象に必要

		double pixelMicron;
		double micron_slice;
		
		//double zScalingFactor;
		int enhancedZsize;

		ImagePlus cv_imp;
		ImagePlus forMakeImage = new ImagePlus();
		
		
		//double tScalingFactor;
		int enhancedTsize;

		String axis; //"T" or "Z"
		double mag; // magnification;
		double gsd; // gussian sd

		
		// new image //
		
		ImagePlus[] img_array;
		ImagePlus newImage;
		Calibration calibration;

		boolean checkMagnification;
		double magnification = 2.0;//画像拡大倍率
		int iterations = 10;
		boolean checkDeconvo;

	public ZTinterpolation(ImagePlus img){
		imp = img;
		
		fi = imp.getOriginalFileInfo(); //fileinfo　と calibrationの違いは？
		//System.out.println(fi.fileName);
		Calibration cal = imp.getCalibration();
		
		width = imp.getWidth();
		height = imp.getHeight();
		c = imp.getNChannels();
		z = imp.getNSlices();
		t = imp.getNFrames();
		b = imp.getBitDepth();

		pixelWidth = cal.pixelWidth;
		pixelHeight = cal.pixelHeight;
		pixelDepth = cal.pixelDepth;
		
		pixelMicron = 1.0 / pixelWidth;
		micron_slice = pixelDepth;

	}

	public void setCheckDeconvolution(boolean b){
		checkDeconvo = b;
	}

	public void setCheckMagnification(boolean b, double m){
		checkMagnification = b;
		magnification = m;
	}

	public void setIterations(int num){
		iterations = num;
	}

	public void makeZinterpolation(double mag1){
		//if(checkDeconvo == true) {
		//	doLucyRichardson(imp);//2023.04.10 まだ調整が必要 x, yの値、拡大することで解像度を上げるなど
		//}


		axis = "Z";
		mag = mag1;
		gsd = mag1 / 2;

		enhancedZsize = (int)(z * mag1);

		
		newImage = new ImagePlus();
	
		//forMakeImage = imp.duplicate();
		forMakeImage = new Duplicator().run(imp); //1.52n対策

		makePseudoSlice();
	}
	
	public void makeTinterpolation(double mag1){
		//if(checkDeconvo == true) {
		//	doLucyRichardson();//2023.04.10 まだ調整が必要 x, yの値、拡大することで解像度を上げるなど
		//}

		axis = "T";
		mag = mag1;
		gsd = mag1/2;

		enhancedTsize = (int)(t * mag1);
		
		cv_imp = convertZtoT(imp);
		cv_imp.getCalibration().pixelDepth = mag1;
				
		newImage = new ImagePlus();

		forMakeImage = cv_imp;

		makePseudoSlice();
		
	}


	public void doLucyRichardson(ImagePlus inputImage){ //一旦拡大してその時使用するgaussanblurのパラメーターでLucy。縮小をするかそのままか？
		long stime = System.currentTimeMillis();
		System.out.println("pixelMicron : " + pixelMicron);
		double prefactor = Math.max(pixelMicron / 10.0, 1.0); //pixelMicronの10分の１で、1.0より小さい場合は1.0とする, 経験則？

		final double factor = prefactor;//confocal用
		//final double factor = 1.0;//STED用に試し

		System.out.println("factor : " + factor);

		IntStream intStream = IntStream.range(0, inputImage.getStackSize());
		//ImageStack stack = new ImageStack();

		AtomicInteger count = new AtomicInteger(0);

		intStream.parallel().forEach(i ->{
			int currentCount = count.incrementAndGet();
			System.out.println("Deconvolution progress: " + currentCount + " / " + inputImage.getStackSize());

			LucyRichardson lucyRichardson = new LucyRichardson(inputImage.getStack().getProcessor(i + 1));
			lucyRichardson.setGaussianBlurProperties(factor, factor, 0.02); // x, yのgsdは任意にできるようにするべき
			ImageProcessor deconvolutionImage = lucyRichardson.getProcessedImage(0, iterations);//filter #1 = gaussian filter
			inputImage.getStack().setProcessor(deconvolutionImage,i + 1);
			//stack.addSlice(deconvolutionImage);
		});
		//ImagePlus test = new ImagePlus();
		//test.setStack(stack);
		//test.show();
		long etime = System.currentTimeMillis();
		System.out.println("deconvoTime : " + (etime - stime) + "msec");
	}

	public ImagePlus resizeImage(ImagePlus img, int width, int height){

		ArrayList<ImageProcessor> imgList = new ArrayList<>();
		ConcurrentHashMap<Integer, ImageProcessor> imgMap = new ConcurrentHashMap<>();

		System.out.println("おひょい : " + img.getStackSize());

		for(int i = 0; i < img.getStackSize(); i++){
			imgList.add(img.getStack().getProcessor(i + 1));
		}

		ArrayList<Integer> count = new ArrayList<>();
		count.add(0);
		IntStream intStream = IntStream.range(0, img.getStackSize());
		intStream.parallel().forEach(i ->{
			count.add(i);
			IJ.showStatus("Resizing : " + count.size() + " / " + img.getStackSize());
			//IJ.showProgress(count.size(), img.getStackSize());
			ImageProcessor buff = imgList.get(i);
			buff.setInterpolate(true);
			buff.setInterpolationMethod(ImageProcessor.BICUBIC);
			imgMap.put(i, buff.resize(width, height));

		});

		ImageStack imageStack = new ImageStack();

		for(int i = 0; i < img.getStackSize(); i++){
			imageStack.addSlice(imgMap.get(i));
		}

		ImagePlus result = new ImagePlus();
		result.setStack(imageStack);
		result.setDimensions(img.getNChannels(), img.getNSlices(), img.getNFrames());

		this.calculateCalibration(img);
		result.setCalibration(calibration);
		result.setTitle(magnification + "x_" + img.getTitle());

		if (img.isComposite()) {
			result = new CompositeImage(result, ((CompositeImage)img).getMode());
			((CompositeImage)result).copyLuts(img);
		}
		if(result.isHyperStack()){
			result.setOpenAsHyperStack(true);
		}
		return result;
	}

	public void calculateCalibration(ImagePlus img) {
		calibration = img.getCalibration();
		calibration.pixelWidth = calibration.pixelWidth / magnification;
		calibration.pixelHeight = calibration.pixelHeight / magnification;
	}

	private void makePseudoSlice(){
		long startTime = System.currentTimeMillis();
		
		// imp の初期位置設定 //
		imp.setZ(1);
		imp.setT(1);
		imp.killRoi();

		img_array = new ImagePlus[height];

		SwingWorker<ImagePlus[], Double> worker = new SwingWorker<ImagePlus[], Double>() {

			@Override
			protected ImagePlus[] doInBackground() throws Exception {

				ArrayList<ImagePlus> sep_imgs = new ArrayList<ImagePlus>();

				//画像の切り抜き//

				for(int y = 0; y < height; y++){

					Roi r = new Roi(0, y , width, 1);
					imp.setRoi(r);
					forMakeImage.setRoi(r);
					//sep_imgs.add(forMakeImage.duplicate());
					sep_imgs.add(new Duplicator().run(forMakeImage));
				}

				/*
				ArrayList<Integer> p_list = new ArrayList<Integer>();
				ImagePlus[] i_array = new ImagePlus[height];
				i_array = sep_imgs.parallelStream().map((Function<ImagePlus, ImagePlus>) buffimp ->{
					p_list.add(1);
					publish((double)p_list.size());
					return OrthogonalMakerMPZ.makeXZimage(buffimp, mag);	
					
				}).toArray(i -> new ImagePlus[i]);
				return i_array;
				*/
				
				OrthogonalMakerMPZ orth = new OrthogonalMakerMPZ(sep_imgs);
				orth.setMagnification(mag);
				orth.setGsd(gsd);
				return orth.makeXZimage();
			}
			
			@Override
			protected void process(List<Double> chunks){
			    for (Double d: chunks) {
			    	IJ.showStatus(String.valueOf("Now Making...." + Math.round((d/height) * 100)) + "%");
			    }
			}
			
			@Override
			public void done() {
				try {
	
					img_array = get();
					long endTime = System.currentTimeMillis();
					System.out.println(endTime - startTime + "msec");	
					imp.killRoi();
					
					convertXZtoXY();
					
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
 				System.out.println("done");
			}
			
		};
		
		worker.execute();
		imp.killRoi();

	}
	
	
	private ImagePlus convertZtoT(ImagePlus img){
		int t = img.getNFrames();
		int z = img.getNSlices();
		int c = img.getNChannels();

		ImageStack buff_stack = new ImageStack(width,height);
		ImagePlus new_img = new ImagePlus();
		
		for(int cz = 0; cz < z; cz++){
			for(int ct = 0; ct < t; ct++){
				for(int cc = 0; cc < c; cc++){
					int idx = img.getStackIndex(cc+1, cz+1, ct+1);					
					ImageProcessor buff = img.getStack().getProcessor(idx).duplicate();
					buff_stack.addSlice(buff);
				}
			}
		}
		new_img.setStack("convertZtoT", buff_stack);
		new_img.setDimensions(c, t, z);

		
		return new_img;
	}
	
	
	ImagePlus newHyperStack = new ImagePlus();
	int zsize = 1;
	int tsize = 1;
	private void convertXZtoXY(){
		long st = System.currentTimeMillis();

		SwingWorker<ImagePlus, Double> worker = new SwingWorker<ImagePlus, Double>() {

			@Override
			protected ImagePlus doInBackground() throws Exception {

				
				if(axis == "Z"){
					newHyperStack = IJ.createHyperStack((imp.getTitle()+"_PseudoZslice"), width, height, c, enhancedZsize, t, b);
					zsize = enhancedZsize;
					tsize = t;
				}else if(axis == "T"){
					newHyperStack = IJ.createHyperStack((imp.getTitle()+"_PseudoTslice"), width, height, c, enhancedTsize, z, b);
					zsize = enhancedTsize;
					tsize = z;
				}
				
				
				IntStream i_stream = IntStream.range(0, height);
				i_stream.parallel().forEach(y ->{
					ImagePlus xzImage = img_array[y];

					for(int ct = 0; ct < tsize; ct++){

						for(int cc = 0; cc < c; cc++){
							int index_b = img_array[0].getStackIndex(cc+1, 1, ct+1);
							ImageProcessor buff_ip = xzImage.getStack().getProcessor(index_b);
							
							for(int ez = 0; ez < zsize; ez++){
								int index = newHyperStack.getStackIndex(cc+1,ez+1, ct+1);
								ImageProcessor hs_ip = newHyperStack.getStack().getProcessor(index);
	
								for(int x = 0; x < width; x++){
									int v = buff_ip.getPixel(x, ez);
									hs_ip.putPixel(x, y, v);
								}
							
							}
						}
					}
					
					
					
					
				});
				
				
				
				return newHyperStack;
				
			}
			
			@Override
			protected void process(List<Double> chunks){
			    for (Double d: chunks) {
			    	IJ.showStatus(String.valueOf("Now Converting...." + Math.round((d/(tsize*c*zsize)) * 100)) + "%");
			    }
			}
			
			@Override
			public void done() {
				long et = System.currentTimeMillis();
				System.out.println(et-st + "msec");
				
				try {

					ImagePlus buff = get();
					buff.show();
					IJ.showProgress(1 );
					if(axis == "T"){
						ImagePlus buff_imp = convertZtoT(buff);
						newImage = HyperStackConverter.toHyperStack(buff_imp, c, z, enhancedTsize);
						newImage.setTitle((imp.getTitle()+"_PseudoTslice"));

					}else if(axis == "Z"){
						newImage = buff;
						newImage.setTitle((imp.getTitle()+"_PseudoZslice"));
					}

					//pseudo画像作ったあとにdeconvolution
					if(checkDeconvo == true) {
						doLucyRichardson(newImage);
					}
					
					//newImage.show();

					/*なんかよくわからない部分。他で使用していないので削除対象 20230517
					if(c < 2){
						
					}else if(imp.isComposite()){

						CompositeImage ci = (CompositeImage)imp;
						CompositeImage ci_new = (CompositeImage)newImage;
						ci_new.setMode(ci.getMode());
						ci_new.setLuts(imp.getLuts());
						
					}
					*/
					
					newImage.setT(1);
					newImage.setZ(1);
					for(int cc = c; cc > 0; cc--){
						newImage.setC(cc);
						newImage.setDisplayRange(imp.getDisplayRangeMin(), imp.getDisplayRangeMax());
					}

					
					// copy the calibration and reconstruct for interval //
                    Calibration cal = imp.getCalibration().copy();

                    if(axis == "Z"){
                        double interval = cal.pixelDepth;
                        double reconstruct_interval = interval / ((double)newImage.getNSlices() / imp.getNSlices());
                        cal.pixelDepth = reconstruct_interval;
                    }else if(axis == "T"){
                        double interval = cal.frameInterval;
                        double reconstruct_interval = interval / ((double)newImage.getNFrames() / imp.getNFrames());
                        cal.frameInterval = reconstruct_interval;
                    }

					newImage.setCalibration(cal);

					newImage.updateAndDraw();

					if(checkMagnification == true){
						resizeImage(newImage, (int) (newImage.getWidth() * magnification), (int) (newImage.getHeight() * magnification)).show();
						newImage = null;
					}else {
						newImage.show();
					}

				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
 				System.out.println("done");
			}
			
			
		};


		worker.execute();

	}


	@Override
	public void close() throws Exception {
		// TODO Auto-generated method stub
		
	}

	
}