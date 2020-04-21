// StringWave.java (C) 2001 by Paul Falstad, www.falstad.com

import java.io.InputStream;
import java.awt.*;
import java.awt.image.ImageProducer;
import java.applet.Applet;
import java.applet.AudioClip;
import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;
import java.io.File;
import java.net.URL;
import java.util.Random;
import java.awt.image.MemoryImageSource;
import java.lang.Math;
import java.awt.event.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

class StringWaveCanvas extends Canvas {
    StringWaveFrame pg;
    StringWaveCanvas(StringWaveFrame p) {
	pg = p;
    }
    public Dimension getPreferredSize() {
	return new Dimension(300,400);
    }
    public void update(Graphics g) {
	pg.updateStringWave(g);
    }
    public void paint(Graphics g) {
	pg.updateStringWave(g);
    }
};

class StringWaveLayout implements LayoutManager {
    public StringWaveLayout() {}
    public void addLayoutComponent(String name, Component c) {}
    public void removeLayoutComponent(Component c) {}
    public Dimension preferredLayoutSize(Container target) {
	return new Dimension(500, 500);
    }
    public Dimension minimumLayoutSize(Container target) {
	return new Dimension(100,100);
    }
    public void layoutContainer(Container target) {
	int barwidth = 0;
	int i;
	for (i = 1; i < target.getComponentCount(); i++) {
	    Component m = target.getComponent(i);
	    if (m.isVisible()) {
		Dimension d = m.getPreferredSize();
		if (d.width > barwidth)
		    barwidth = d.width;
	    }
	}
	Insets insets = target.insets();
	int targetw = target.size().width - insets.left - insets.right;
	int cw = targetw-barwidth;
	int targeth = target.size().height - (insets.top+insets.bottom);
	target.getComponent(0).move(insets.left, insets.top);
	target.getComponent(0).resize(cw, targeth);
	cw += insets.left;
	int h = insets.top;
	for (i = 1; i < target.getComponentCount(); i++) {
	    Component m = target.getComponent(i);
	    if (m.isVisible()) {
		Dimension d = m.getPreferredSize();
		if (m instanceof Scrollbar)
		    d.width = barwidth;
		if (m instanceof Label) {
		    h += d.height/5;
		    d.width = barwidth;
		}
		m.move(cw, h);
		m.resize(d.width, d.height);
		h += d.height;
	    }
	}
    }
};

public class StringWave extends Applet implements ComponentListener {
    static StringWaveFrame ogf;
    void destroyFrame() {
	if (ogf != null)
	    ogf.dispose();
	ogf = null;
	repaint();
    }
    boolean started = false;
    public void init() {
	addComponentListener(this);
    }
    
    public static void main(String args[]) {
        ogf = new StringWaveFrame(null);
        ogf.init();
    }

    void showFrame() {
	if (ogf == null) {
	    started = true;
	    ogf = new StringWaveFrame(this);
	    ogf.init();
	    repaint();
	}
    }
    
    public void paint(Graphics g) {
	String s = "Applet is open in a separate window.";
	if (!started)
	    s = "Applet is starting.";
	else if (ogf == null)
	    s = "Applet is finished.";
	else
	    ogf.show();
	g.drawString(s, 10, 30);
    }
    
    public void componentHidden(ComponentEvent e){}
    public void componentMoved(ComponentEvent e){}
    public void componentShown(ComponentEvent e) { showFrame(); }
    public void componentResized(ComponentEvent e) {}
    
    public void destroy() {
	if (ogf != null)
	    ogf.dispose();
	ogf = null;
	repaint();
    }
};

class StringWaveFrame extends Frame
  implements ComponentListener, ActionListener, AdjustmentListener,
             MouseMotionListener, MouseListener, ItemListener {
    
    PlayThread playThread;

    Dimension winSize;
    Image dbimage;
    
    Random random;
    int maxTerms = 60;
    int maxMaxTerms = 160;
    int sampleCount;
    double sinTable[][];
    public static final double epsilon = .00001;
    public static final double epsilon2 = .003;
    
    public String getAppletInfo() {
	return "StringWave Series by Paul Falstad";
    }

    Button sineButton;
    Button triangleButton;
    Button blankButton;
    Button resonanceButton;
    Checkbox stoppedCheck;
    Checkbox forceCheck;
    Checkbox soundCheck;
    Checkbox touchCheck;
    Checkbox backwardsCheck;
    Checkbox logCheck;
    Choice modeChooser;
    Choice displayChooser;
    Scrollbar dampingBar;
    Scrollbar speedBar;
    Scrollbar forceBar;
    Scrollbar loadBar;
    Scrollbar tensionBar;
    double magcoef[];
    double dampcoef;
    double phasecoef[];
    double phasecoefcos[];
    double phasecoefadj[];
    //double magcoefdisp[];
    //double phasecoefdisp[];
    //double phasecoefcosdisp[];
    double forcebasiscoef[];
    double omega[];
    static final double pi = 3.14159265358979323846;
    double step;
    double func[];
    double funci[];
    int selectedCoef;
    int magnitudesY;
    static final int SEL_NONE = 0;
    static final int SEL_FUNC = 1;
    static final int SEL_MAG = 2;
    static final int MODE_PLUCK = 0;
    static final int MODE_SHAPE = 1;
    static final int MODE_TOUCH = 997;
    static final int MODE_FORCE = 998;
    static final int MODE_BOW = 999;
    static final int DISP_PHASE = 0;
    static final int DISP_LEFTRIGHT = 1;
    static final int DISP_PHASECOS = 2;
    static final int DISP_PHASORS = 3;
    static final int DISP_MODES = 4;
    int selection;
    int dragX, dragY;
    boolean dragging;
    boolean bowing;
    boolean bowCaught;
    boolean forceApplied;
    double t;
    double forceMag;
    int pause;
    int forceBarValue;
    double forceTimeZero;
    int tensionBarValue;
    Color gray1 = new Color(76,  76,  76);
    Color gray2 = new Color(127, 127, 127);
    

    int getrand(int x) {
	int q = random.nextInt();
	if (q < 0) q = -q;
	return q % x;
    }
    StringWaveCanvas cv;
    StringWave applet;
    boolean java2;

    StringWaveFrame(StringWave a) {
	super("Loaded String Applet v1.5a");
	applet = a;
    }

    public void init() {
        String jv = System.getProperty("java.class.version");
        double jvf = new Double(jv).doubleValue();
        if (jvf >= 48)
	    java2 = true;
	
	selectedCoef = -1;
	setLayout(new StringWaveLayout());
	cv = new StringWaveCanvas(this);
	cv.addComponentListener(this);
	cv.addMouseMotionListener(this);
	cv.addMouseListener(this);
	add(cv);
	add(sineButton = new Button("Fundamental"));
	sineButton.addActionListener(this);
	add(triangleButton = new Button("Center Pluck"));
	triangleButton.addActionListener(this);
	add(blankButton = new Button("Clear"));
	blankButton.addActionListener(this);
	stoppedCheck = new Checkbox("Stopped");
	stoppedCheck.addItemListener(this);
	add(stoppedCheck);
	forceCheck = new Checkbox("Driving Force", false);
	forceCheck.addItemListener(this);
	add(forceCheck);
	soundCheck = new Checkbox("Sound", false);
	soundCheck.addItemListener(this);
	if (java2)
	    add(soundCheck);
	touchCheck = new Checkbox("Touched in Center", false);
	touchCheck.addItemListener(this);
	/*add(touchCheck);*/
	backwardsCheck = new Checkbox("Run Backwards", false);
	backwardsCheck.addItemListener(this);
	// add(backwardsCheck);
	logCheck = new Checkbox("Log View", false);
	logCheck.addItemListener(this);
	add(logCheck);
	
	modeChooser = new Choice();
	modeChooser.add("Mouse = Pluck string");
	modeChooser.add("Mouse = Shape string");
	/*modeChooser.add("Mouse = Touch string");
	  modeChooser.add("Mouse = Apply force");*/
	modeChooser.addItemListener(this);
	add(modeChooser);

	displayChooser = new Choice();
	displayChooser.add("Display Phases");
	displayChooser.add("Display Left+Right");
	displayChooser.add("Display Phase Cosines");
	displayChooser.add("Display Phasors");
	displayChooser.add("Display Modes");
	displayChooser.addItemListener(this);
	add(displayChooser);

	add(new Label("Simulation Speed", Label.CENTER));
	add(speedBar = new Scrollbar(Scrollbar.HORIZONTAL, 85, 1, 1, 200));
	speedBar.addAdjustmentListener(this);

	add(new Label("Damping", Label.CENTER));
	add(dampingBar = new Scrollbar(Scrollbar.HORIZONTAL, 10, 1, 0, 400));
	dampingBar.addAdjustmentListener(this);

	add(new Label("Force Frequency", Label.CENTER));
	forceBarValue = 5;
	add(forceBar = new Scrollbar(Scrollbar.HORIZONTAL,
				     forceBarValue, 1, 1, 30));
	forceBar.addAdjustmentListener(this);

	add(resonanceButton = new Button("Resonance Freq"));
	resonanceButton.addActionListener(this);

	add(new Label("Number of Loads", Label.CENTER));
	add(loadBar = new Scrollbar(Scrollbar.HORIZONTAL,
				    maxTerms, 1, 2, maxMaxTerms));
	loadBar.addAdjustmentListener(this);
	setLoadCount();

	tensionBarValue = 16;
	add(new Label("Tension", Label.CENTER));
	add(tensionBar = new Scrollbar(Scrollbar.HORIZONTAL,
				       tensionBarValue, 1, 1, 100));
	tensionBar.addAdjustmentListener(this);

	try {
	    String param = applet.getParameter("PAUSE");
	    if (param != null)
		pause = Integer.parseInt(param);
	} catch (Exception e) { }
	
	magcoef = new double[maxMaxTerms];
	phasecoef = new double[maxMaxTerms];
	phasecoefcos = new double[maxMaxTerms];
	phasecoefadj = new double[maxMaxTerms];
	//magcoefdisp = new double[maxMaxTerms];
	//phasecoefdisp = new double[maxMaxTerms];
	//phasecoefcosdisp = new double[maxMaxTerms];
	forcebasiscoef = new double[maxMaxTerms];
	func = new double[maxMaxTerms+1];
	funci = new double[maxMaxTerms+1];

	random = new Random();
	setDamping();
	reinit();
	cv.setBackground(Color.black);
	cv.setForeground(Color.lightGray);
	resize(800, 600);
	handleResize();
	Dimension x = getSize();
	Dimension screen = getToolkit().getScreenSize();
	setLocation((screen.width  - x.width)/2,
		    (screen.height - x.height)/2);
	show();
    }

    void reinit() {
	doSine();
    }

    void handleResize() {
        Dimension d = winSize = cv.getSize();
	if (winSize.width == 0)
	    return;
	dbimage = createImage(d.width, d.height);
    }
    
    void doSine() {
	int x;
	for (x = 0; x != sampleCount; x++) {
	    func[x] = java.lang.Math.sin(x*step);
	}
	func[sampleCount] = func[0];
	transform(true);
    }

    void doTriangle() {
	int x;
	for (x = 0; x <= sampleCount/2; x++)
	    func[sampleCount-x] = func[x] = 2.0*x/sampleCount;
	func[sampleCount] = func[0];
	transform(true);
    }

    void doBlank() {
	int x;
	for (x = 0; x <= sampleCount; x++)
	    func[x] = 0;
	transform(true);
    }

    void transform(boolean novel) {
	int x, y;
	t = 0;
	for (y = 1; y != maxTerms; y++) {
	    double a = 0;
	    double b = 0;
	    for (x = 1; x != sampleCount; x++) {
		a += sinTable[x][y]*func[x];
		b -= sinTable[x][y]*funci[x];
	    }
	    a *= 2.0/sampleCount;
	    b *= 2.0/(sampleCount*omega[y]);
	    if (a < epsilon && a > -epsilon) a = 0;
	    if (b < epsilon && b > -epsilon) b = 0;
	    if (novel)
		b = 0;
	    double r = java.lang.Math.sqrt(a*a+b*b);
	    magcoef[y] = r;
	    double ph2 = java.lang.Math.atan2(b, a);
	    phasecoefadj[y] = ph2;
	    phasecoef[y] = ph2;
	}
	updateSound();
    }

    void updateSound() {
	if (playThread != null)
	    playThread.soundChanged();
    }
    
    int getPanelHeight() { return winSize.height / 3; }

    void centerString(Graphics g, String s, int y) {
	FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (winSize.width-fm.stringWidth(s))/2, y);
    }

    public void paint(Graphics g) {
	cv.repaint();
    }

    long lastTime;
    
    public void updateStringWave(Graphics realg) {
	if (winSize == null || winSize.width == 0)
	    return;
	Graphics g = dbimage.getGraphics();
	boolean allQuiet = true;
	double dampmult = 1;
	if (!stoppedCheck.getState()) {
	    if (bowing) {
		doBow();
		allQuiet = false;
	    }
	    int val = speedBar.getValue();
	    //if (val > 40)
	    //val += getrand(10);
	    if (forceCheck.getState()) {
		doForce();
		allQuiet = false;
	    } else
		forceMag = 0;
	    long sysTime = System.currentTimeMillis();
	    double tadd = 0;
	    if (lastTime != 0)
		tadd = java.lang.Math.exp(val/20.)*(.1/1500)*(sysTime-lastTime);
	    if (backwardsCheck.getState())
		t -= tadd;
	    else
		t += tadd;
	    lastTime = sysTime;
	    dampmult = Math.exp(dampcoef*tadd);
	} else
	    lastTime = 0;
	g.setColor(cv.getBackground());
	g.fillRect(0, 0, winSize.width, winSize.height);
	g.setColor(cv.getForeground());
	int i;
	int ox = -1, oy = -1;
	int panelHeight = getPanelHeight();
	int midy = panelHeight / 2;
	int halfPanel = panelHeight / 2;
	double ymult = .75 * halfPanel;
	for (i = -1; i <= 1; i++) {
	    g.setColor((i == 0) ? gray2 : gray1);
	    g.drawLine(0,             midy+(i*(int) ymult),
		       winSize.width, midy+(i*(int) ymult));
	}
	g.setColor(gray2);
	g.drawLine(winSize.width/2, midy-(int) ymult,
		   winSize.width/2, midy+(int) ymult);
	if (dragging && selection == SEL_FUNC) {
	    g.setColor(Color.cyan);
	    allQuiet = true;
	    for (i = 0; i != sampleCount+1; i++) {
		int x = winSize.width * i / sampleCount;
		int y = midy - (int) (ymult * func[i]);
		if (ox != -1)
		    g.drawLine(ox, oy, x, y);
		ox = x;
		oy = y;
	    }
	}
	if (!stoppedCheck.getState()) {
	    if (touchCheck.getState())
		doTouch();
	    for (i = 1; i != maxTerms; i++)
		magcoef[i] *= dampmult;
	}

	double magcoefdisp[] = magcoef;
	double phasecoefdisp[] = phasecoef;
	double phasecoefcosdisp[] = phasecoefcos;

	if (dragging && selection == SEL_FUNC) {
	    lastTime = 0;
	} else {
	    g.setColor(Color.white);
	    ox = -1;
	    int j;
	    for (j = 1; j != maxTerms; j++) {
		if (magcoef[j] < epsilon && magcoef[j] > -epsilon) {
		    magcoef[j] = phasecoef[j] = phasecoefadj[j] = 0;
		    continue;
		}
		allQuiet = false;
		phasecoef[j] = (omega[j]*t+phasecoefadj[j]) % (2*pi);
		if (phasecoef[j] > pi)
		    phasecoef[j] -= 2*pi;
		else if (phasecoef[j] < -pi)
		    phasecoef[j] += 2*pi;
		phasecoefcos[j] = java.lang.Math.cos(phasecoef[j]);
	    }
	    if (forceApplied) {
		allQuiet = false;
		magcoefdisp = new double[maxTerms];
		phasecoefdisp = new double[maxTerms];
		phasecoefcosdisp = new double[maxTerms];
		for (i = 1; i < maxTerms; i++) {
		    double ph = phasecoef[i];
		    double a = magcoef[i] * phasecoefcos[i];
		    double b = magcoef[i] * java.lang.Math.sin(ph);
		    a += forcebasiscoef[i];
		    double r = java.lang.Math.sqrt(a*a+b*b);
		    magcoefdisp[i] = r;
		    double ph2 = java.lang.Math.atan2(b, a);
		    phasecoefdisp[i] += ph2;
		    phasecoefcosdisp[i] = (r > 0) ? a/r : 0;
		}
	    }

	    int dotSize = (sampleCount < 40) ? 5 : 0;
	    int funcDotSize = dotSize;
	    int forcePos = (forceMag == 0) ? -1 : sampleCount/2;
	    for (i = 0; i != sampleCount+1; i++) {
		int x = winSize.width * i / sampleCount;
		double dy = 0;
		for (j = 1; j != maxTerms; j++)
		    dy += magcoefdisp[j] *
			sinTable[i][j] * phasecoefcosdisp[j];
		func[i] = dy;
		int y = midy - (int) (ymult * dy);
		if (ox != -1)
		    g.drawLine(ox, oy, x, y);
		if (dotSize > 0 && i != 0 && i != sampleCount)
		    g.fillOval(x-dotSize/2, y-dotSize/2, dotSize, dotSize);
		if (i == forcePos) {
		    int yl = (int) (ymult*forceMag*8);
		    if (yl > 7 || yl < -7) {
			int y2 = y - yl;
			int forcedir = (forceMag < 0) ? -1 : 1;
			g.drawLine(x, y, x, y2);
			g.drawLine(x, y2, x+5, y2+5*forcedir);
			g.drawLine(x, y2, x-5, y2+5*forcedir);
		    }
		}
		ox = x;
		oy = y;
	    }
	}
	if (selectedCoef != -1 && !dragging &&
	    (magcoefdisp[selectedCoef] > .04 ||
	     magcoefdisp[selectedCoef] < -.04)) {
	    g.setColor(Color.yellow);
	    ox = -1;
	    ymult *= magcoefdisp[selectedCoef];
	    for (i = 0; i != sampleCount+1; i++) {
		int x = winSize.width * i / sampleCount;
		double dy = sinTable[i][selectedCoef] *
		    phasecoefcosdisp[selectedCoef];
		int y = midy - (int) (ymult * dy);
		if (ox != -1)
		    g.drawLine(ox, oy, x, y);
		ox = x;
		oy = y;
	    }
	}
	int termWidth = getTermWidth();
	ymult = .6 * halfPanel;
	g.setColor(Color.white);
	if (displayChooser.getSelectedIndex() == DISP_PHASE ||
	    displayChooser.getSelectedIndex() == DISP_PHASECOS)
	    magnitudesY = panelHeight;
	else
	    magnitudesY = panelHeight*2;
	midy = magnitudesY + (panelHeight / 2) + (int) ymult/2;
	centerString(g, "Harmonics: Magnitudes", magnitudesY+(int) (panelHeight*.16));
	g.setColor(gray2);
	g.drawLine(0, midy, winSize.width, midy);
	g.setColor(gray1);
	g.drawLine(0, midy-(int)ymult, winSize.width, midy-(int) ymult);
	g.drawLine(0, midy+(int)ymult, winSize.width, midy+(int) ymult);
	int dotSize = termWidth-3;
	if (dotSize < 3)
	    dotSize = 3;
	if (dotSize > 9)
	    dotSize = 9;
	for (i = 1; i != maxTerms; i++) {
	    int t = termWidth * (i-1) + termWidth/2;
	    int y = midy - (int) (logcoef(magcoefdisp[i])*ymult);
	    g.setColor(i == selectedCoef ? Color.yellow : Color.white);
	    g.drawLine(t, midy, t, y);
	    g.fillOval(t-dotSize/2, y-dotSize/2, dotSize, dotSize);
	}

	if (displayChooser.getSelectedIndex() == DISP_PHASE ||
	    displayChooser.getSelectedIndex() == DISP_PHASECOS) {
	    g.setColor(Color.white);
	    boolean cosines =
		displayChooser.getSelectedIndex() == DISP_PHASECOS;
	    centerString(g, cosines ? "Harmonics: Phase Cosines" : "Harmonics: Phases",
			 (int) (panelHeight*2.10));
	    ymult = .75 * halfPanel;
	    midy = ((panelHeight * 5) / 2);
	    for (i = -2; i <= 2; i++) {
		if (cosines && (i == 1 || i == -1))
		    continue;
		g.setColor((i == 0) ? gray2 : gray1);
		g.drawLine(0,             midy+(i*(int) ymult)/2,
			   winSize.width, midy+(i*(int) ymult)/2);
	    }
	    if (!cosines)
		ymult /= pi;
	    for (i = 1; i != maxTerms; i++) {
		int t = termWidth * (i-1) + termWidth/2;
		double ph = (cosines) ? phasecoefcosdisp[i] : phasecoefdisp[i];
		if (magcoef[i] > -epsilon2/4 && magcoefdisp[i] < epsilon2/4)
		    ph = 0;
		int y = midy - (int) (ph*ymult);
		g.setColor(i == selectedCoef ? Color.yellow : Color.white);
		g.drawLine(t, midy, t, y);
		g.fillOval(t-dotSize/2, y-dotSize/2, dotSize, dotSize);
	    }
	} else if (displayChooser.getSelectedIndex() == DISP_LEFTRIGHT) {
	    midy = panelHeight + panelHeight/2;
	    halfPanel = panelHeight/2;
	    ymult = .75 * halfPanel;
	    for (i = -1; i <= 1; i++) {
		g.setColor((i == 0) ? gray2 : gray1);
		g.drawLine(0,             midy+(i*(int) ymult),
			   winSize.width, midy+(i*(int) ymult));
	    }
	    g.setColor(gray2);
	    g.drawLine(winSize.width/2, midy-(int) ymult,
		       winSize.width/2, midy+(int) ymult);
	    ox = -1;
	    int oy2 = -1;
	    int subsamples = 4;
	    for (i = 0; i != sampleCount*subsamples+1; i++) {
		int x = winSize.width * i / (subsamples*sampleCount);
		double dy1 = 0;
		double dy2 = 0;
		int j;
		double stepi = step*i/subsamples;
		for (j = 1; j != maxTerms; j++) {
		    if (magcoefdisp[j] == 0)
			continue;
		    double stepij = stepi*j;
		    double dp = magcoefdisp[j]*.5;
		    double phase = phasecoefdisp[j];
		    dy1 += dp*java.lang.Math.sin(stepij+phase);
		    dy2 += dp*java.lang.Math.sin(stepij-phase);
		}
		int y1 = midy - (int) (ymult * dy1);
		int y2 = midy - (int) (ymult * dy2);
		if (ox != -1) {
		    g.setColor(Color.cyan);
		    g.drawLine(ox, oy,  x, y1);
		    g.setColor(Color.green);
		    g.drawLine(ox, oy2, x, y2);
		}
		ox = x;
		oy  = y1;
		oy2 = y2;
	    }
	} else if (displayChooser.getSelectedIndex() == DISP_PHASORS) {
	    int sqw = (winSize.width-25)/3;
	    int sqh = sqw;
	    int y = panelHeight+(panelHeight-sqh)/2;
	    dotSize = 5;
	    for (i = 1; i <= 3; i++) {
		g.setColor(gray2);
		int leftX = (sqw+10)*(i-1);
		int centerX = leftX+sqw/2;
		int centerY = y+sqh/2;
		g.drawLine(leftX, centerY, leftX+sqw, centerY);
		g.drawLine(centerX, y, centerX, y+sqh);
		g.setColor(gray1);
		g.drawOval(centerX-sqw/2, centerY-sqh/2, sqw, sqh);
		g.setColor(i == selectedCoef ? Color.yellow : Color.white);
		g.drawRect(leftX, y, sqw, sqh);
		boolean getFx = (forceApplied || forceCheck.getState());
		int ax = (int) (phasecoefcosdisp[i]*magcoefdisp[i]*sqw*.5);
		int ay = (int) (java.lang.Math.sin(phasecoefdisp[i])*
				magcoefdisp[i]*sqh*.5);
		int fx = (getFx) ? ((int) (forcebasiscoef[i]*sqw*.5)) : 0;
		g.drawLine(centerX+fx, centerY, centerX+ax, centerY-ay);
		g.fillOval(centerX+ax-dotSize/2, centerY-ay-dotSize/2,
			   dotSize, dotSize);
	    }
	} else if (displayChooser.getSelectedIndex() == DISP_MODES) {
	    int sqw = (winSize.width-25)/3;
	    int sqh = (int) (sqw/pi);
	    int topY = panelHeight;
	    int leftX = 0;
	    for (i = 1; i < sampleCount; i++) {
		if (!(magcoefdisp[i] > .06 ||
		      magcoefdisp[i] < -.06))
		    continue;
		g.setColor(gray2);
		int centerX = leftX+sqw/2;
		int centerY = topY+sqh/2;
		g.drawLine(leftX, centerY, leftX+sqw, centerY);
		g.drawLine(centerX, topY, centerX, topY+sqh);
		g.setColor(i == selectedCoef ? Color.yellow : Color.white);
		g.drawRect(leftX, topY, sqw, sqh);
		ox = -1;
		ymult = sqh*.5*magcoefdisp[i];
		int j;
		for (j = 0; j != sampleCount+1; j++) {
		    int x = leftX + sqw * j / sampleCount;
		    double dy = sinTable[j][i] * phasecoefcosdisp[i];
		    int y = centerY - (int) (ymult * dy);
		    if (ox != -1)
			g.drawLine(ox, oy, x, y);
		    ox = x;
		    oy = y;
		}
		leftX += sqw+10;
		if (leftX + sqw > winSize.width) {
		    leftX = 0;
		    topY += sqh + 10;
		    if (topY+sqh > panelHeight*2)
			break;
		}
	    }
	}
	realg.drawImage(dbimage, 0, 0, this);
	if (!stoppedCheck.getState() && !allQuiet)
	    cv.repaint(pause);
    }

    int getTermWidth() {
	int termWidth = winSize.width / maxTerms;
	if (termWidth < 2)
	    termWidth = 2;
	int maxTermWidth = winSize.width/30;
	if (termWidth > maxTermWidth)
	    termWidth = maxTermWidth;
	termWidth &= ~1;
	return termWidth;
    }

    void getVelocities() {
	int k, j;
	for (j = 0; j != sampleCount; j++) {
	    double dy = 0;
	    for (k = 0; k != sampleCount; k++)
		dy += magcoef[k] * sinTable[j][k] *
		    java.lang.Math.sin(phasecoef[k]) * omega[k];
	    funci[j] = -dy;
	}
    }

    void setForce() {
	// adjust time zero to maintain continuity in the force func
	// even though the frequency has changed.
	double oldfreq = forceBarValue * omega[1] / 20.0;
	forceBarValue = forceBar.getValue();
	double newfreq = forceBarValue * omega[1] / 20.0;
	double adj = newfreq-oldfreq;
	forceTimeZero = t-oldfreq*(t-forceTimeZero)/newfreq;
    }

    void doForce() {
	double freq = forceBar.getValue() * omega[1] / 20.0;
	// was .01
	forceMag = java.lang.Math.cos((t-forceTimeZero)*freq)*.06;
	if (forceBar.getValue() == 1)
	    forceMag *= 2;
	applyForce(maxTerms/2, forceMag);
    }

    void doTouch() {
	int x = sampleCount/2;
	double lim = .1;
	double val = func[x];
	double force = 0;
	if (val > lim)
	    force = -(val-lim);
	else if (val < -lim)
	    force = -(val+lim);
	else
	    return;
	int y;
	for (y = 1; y != maxTerms; y++) {
	    double coef = 0;
	    for (int j = 1; j != sampleCount; j++) {
		double f = (j <= x) ? force*j/x :
		    force*(sampleCount-j)/(sampleCount-x);
		coef += sinTable[j][y]*f;
	    }
	    coef *= 2.0/sampleCount;

	    double ph = phasecoefadj[y]+omega[y]*t; // XXX change elsewhere
	    /// XXX outside loop, elsewhere too
	    double a = magcoef[y] * java.lang.Math.cos(ph);
	    double b = magcoef[y] * java.lang.Math.sin(ph);
	    a += coef;
	    double r = java.lang.Math.sqrt(a*a+b*b);
	    magcoef[y] = r;
	    double ph2 = java.lang.Math.atan2(b, a);
	    phasecoefadj[y] += ph2-ph;
	}
    }

    void edit(MouseEvent e) {
	if (selection == SEL_NONE)
	    return;
	int x = e.getX();
	int y = e.getY();
	switch (selection) {
	case SEL_MAG:   editMag(x, y);   break;
	case SEL_FUNC:  editFunc(x, y);  break;
	}
    }

    void editMag(int x, int y) {
	if (selectedCoef == -1)
	    return;
	int panelHeight = getPanelHeight();
	double ymult = .6 * panelHeight/2;
	double midy = magnitudesY + (panelHeight / 2) + (int) ymult/2;
	double coef = -(y-midy) / ymult;
	coef = unlogcoef(coef);
	if (coef < -1)
	    coef = -1;
	if (coef > 1)
	    coef = 1;
	if (magcoef[selectedCoef] == coef)
	    return;
	magcoef[selectedCoef] = coef;
	updateSound();
	cv.repaint(pause);
    }

    void editFunc(int x, int y) {
	if (modeChooser.getSelectedIndex() == MODE_PLUCK) {
	    editFuncPluck(x, y);
	    return;
	}
	if (modeChooser.getSelectedIndex() == MODE_TOUCH) {
	    editFuncTouch(x, y);
	    return;
	}
	if (modeChooser.getSelectedIndex() == MODE_BOW) {
	    editFuncBow(x, y);
	    return;
	}
	if (modeChooser.getSelectedIndex() == MODE_FORCE) {
	    forceCheck.setState(false);
	    editFuncForce(x, y);
	    return;
	}
	if (dragX == x) {
	    editFuncPoint(x, y);
	    dragY = y;
	} else {
	    // need to draw a line from old x,y to new x,y and
	    // call editFuncPoint for each point on that line.  yuck.
	    int x1 = (x < dragX) ? x : dragX;
	    int y1 = (x < dragX) ? y : dragY;
	    int x2 = (x > dragX) ? x : dragX;
	    int y2 = (x > dragX) ? y : dragY;
	    dragX = x;
	    dragY = y;
	    for (x = x1; x <= x2; x++) {
		y = y1+(y2-y1)*(x-x1)/(x2-x1);
		editFuncPoint(x, y);
	    }
	}
    }
    
    void editFuncTouch(int xx, int yy) {
	dragging = false;
	int panelHeight = getPanelHeight();
	int midy = panelHeight / 2;
	int halfPanel = panelHeight / 2;
	int periodWidth = winSize.width;
	double ymult = .75 * halfPanel;
	int x = xx * sampleCount / periodWidth;
	double val = (midy - yy) / ymult;
	if (val > 1)
	    val = 1;
	if (val < -1)
	    val = -1;
	if (x < 1 || x >= sampleCount)
	    return;
	//if (val > 0 && func[x] < val)
	//  return;
	//if (val < 0 && func[x] > val)
	//  return;
	int y;
	for (y = 1; y != maxTerms; y++) {
	    double coef1 = sinTable[x][y];
	    if (coef1 < 0)
		coef1 = -coef1;
	    double coef = magcoef[y]*coef1;
	    if (coef < 0)
		coef = -coef;
	    double f = .02;
	    if (coef < f)
		continue;
	    int sign = (magcoef[y] < 0) ? -1 : 1;
	    magcoef[y] = sign*f/coef1;
	}
    }

    void editFuncForce(int xx, int yy) {
	dragging = false;
	int panelHeight = getPanelHeight();
	int midy = panelHeight / 2;
	int halfPanel = panelHeight / 2;
	int periodWidth = winSize.width;
	double ymult = .75 * halfPanel;
	int x = xx * sampleCount / periodWidth;
	if (x < 1 || x >= sampleCount)
	    return;
	double val = (midy - yy) / ymult;
	if (val > 1)
	    val = 1;
	if (val < -1)
	    val = -1;
	soundCheck.setState(false);
	applyForce(x, val);
	cv.repaint(pause);
    }

    void applyForce(int x, double val) {
	int y;
	for (y = 1; y != maxTerms; y++) {
	    double coef = 0;
	    for (int j = 1; j != sampleCount; j++) {
		double f = (j <= x) ? val*j/x :
		    val*(sampleCount-j)/(sampleCount-x);
		coef += sinTable[j][y]*f;
	    }
	    coef *= 2.0/sampleCount;

	    double ph = phasecoefadj[y]+omega[y]*t; // XXX change elsewhere
	    double a = magcoef[y] * phasecoefcos[y];
	    double b = magcoef[y] * java.lang.Math.sin(ph);
	    if (forceApplied)
		a += forcebasiscoef[y];
	    a -= coef;
	    double r = java.lang.Math.sqrt(a*a+b*b);
	    if (r > 2) r = 2;
	    magcoef[y] = r;
	    double ph2 = java.lang.Math.atan2(b, a);
	    phasecoefadj[y] += ph2-ph;
	    forcebasiscoef[y] = coef;
	}
	forceApplied = true;
    }

    void forceAppliedOff() {
	if (!forceApplied)
	    return;
	forceApplied = false;
	for (int i = 1; i < maxTerms; i++) {
	    double ph = phasecoefadj[i]+omega[i]*t; // XXX change elsewhere
	    double a = magcoef[i] * java.lang.Math.cos(ph);
	    double b = magcoef[i] * java.lang.Math.sin(ph);
	    a += forcebasiscoef[i];
	    double r = java.lang.Math.sqrt(a*a+b*b);
	    magcoef[i] = r;
	    double ph2 = java.lang.Math.atan2(b, a);
	    phasecoefadj[i] += ph2-ph;
	}
    }

    void editFuncBow(int xx, int yy) {
	dragging = false;
	bowing = true;
	dragX = xx;
	dragY = yy;
	bowCaught = true;
	cv.repaint(pause);
    }

    void doBow() {
	if (!bowCaught)
	    return;
	int panelHeight = getPanelHeight();
	int midy = panelHeight / 2;
	int halfPanel = panelHeight / 2;
	int periodWidth = winSize.width;
	double ymult = .75 * halfPanel;
	int x = dragX * sampleCount / periodWidth;
	double val = (midy - dragY) / ymult;
	if (val < 0)
	    val = -val;
	double bowvel = .4;
	if (bowCaught && func[x] > val) {
	    bowCaught = false;
	    forceAppliedOff();
	    return;
	}
	double p = func[x]+bowvel;
	applyForce(x, p);
    }

    double logep2 = 0;
    double logcoef(double x) {
	if (!logCheck.getState())
	    return x;
	if (x == 0)
	    return x;
	int sign = (x < 0) ? -1 : 1;
	double lg = Math.log(x*sign);
	lg = 1+lg*.1;
	if (lg < 0)
	    return 0;
	return sign*lg;
    }

    double unlogcoef(double x) {
	if (!logCheck.getState())
	    return x;
	if (x == 0)
	    return x;
	int sign = (x < 0) ? -1 : 1;
	double ex = Math.exp((x*sign-1)*10);
	return ex*sign;
    }

    void editFuncPoint(int x, int y) {
	int panelHeight = getPanelHeight();
	int midy = panelHeight / 2;
	int halfPanel = panelHeight / 2;
	int periodWidth = winSize.width;
	double ymult = .75 * halfPanel;
	int lox = x * sampleCount / periodWidth;
	int hix = ((x+1) * sampleCount-1) / periodWidth;
	double val = (midy - y) / ymult;
	if (val > 1)
	    val = 1;
	if (val < -1)
	    val = -1;
	if (lox < 1)
	    lox = 1;
	if (hix >= sampleCount)
	    hix = sampleCount-1;
	for (; lox <= hix; lox++) {
	    func[lox] = val;
	    funci[lox] = 0;
	}
	func[sampleCount] = func[0];
	cv.repaint(pause);
	if (soundCheck.getState() == false)
	    transform(false);
    }

    void editFuncPluck(int x, int y) {
	int panelHeight = getPanelHeight();
	int midy = panelHeight / 2;
	int halfPanel = panelHeight / 2;
	int periodWidth = winSize.width;
	double ymult = .75 * halfPanel;
	int ax = x * sampleCount / periodWidth;
	double val = (midy - y) / ymult;
	if (val > 1)
	    val = 1;
	if (val < -1)
	    val = -1;
	if (ax < 1 || ax >= sampleCount)
	    return;
	int i;
	for (i = 0; i <= ax; i++)
	    func[i] = val*i/ax;
	int bx = sampleCount-ax;
	for (i = ax+1; i < sampleCount; i++)
	    func[i] = val*(sampleCount-i)/bx;
	for (i = 0; i <= sampleCount; i++)
	    funci[i] = 0;
	func[sampleCount] = func[0];
	cv.repaint(pause);
	if (soundCheck.getState() == false)
	    transform(false);
    }

    public void componentHidden(ComponentEvent e){}
    public void componentMoved(ComponentEvent e){}
    public void componentShown(ComponentEvent e) {
	cv.repaint(pause);
    }

    public void componentResized(ComponentEvent e) {
	handleResize();
	cv.repaint(pause);
    }
    public void actionPerformed(ActionEvent e) {
	if (e.getSource() == triangleButton) {
	    doTriangle();
	    cv.repaint();
	}
	if (e.getSource() == sineButton) {
	    doSine();
	    cv.repaint();
	}
	if (e.getSource() == blankButton) {
	    doBlank();
	    cv.repaint();
	}
	if (e.getSource() == resonanceButton) {
	    forceBar.setValue(20);
	    setForce();
	}
    }

    public void adjustmentValueChanged(AdjustmentEvent e) {
	System.out.print(((Scrollbar) e.getSource()).getValue() + "\n");
	if (e.getSource() == dampingBar || e.getSource() == speedBar)
	    setDamping();
	if (e.getSource() == loadBar) {
	    setLoadCount();
	    updateSound();
	}
	if (e.getSource() == forceBar)
	    setForce();
	if (e.getSource() == tensionBar) {
	    setTension();
	    updateSound();
	}
	cv.repaint(pause);
    }

    public boolean handleEvent(Event ev) {
        if (ev.id == Event.WINDOW_DESTROY) {
            applet.destroyFrame();
            return true;
        }
        return super.handleEvent(ev);
    }

    void setTension() {
	int oldTension = tensionBarValue;
	tensionBarValue = tensionBar.getValue();
	double mult = java.lang.Math.sqrt(oldTension/(double) tensionBarValue);
	double roottens = java.lang.Math.sqrt((double) tensionBarValue);
	for (int i = 1; i != maxTerms; i++) {
	    magcoef[i] *= mult;
	    double oldomegat = omega[i] * t;
	    omega[i] = 5*roottens*
		java.lang.Math.sin(i*(3.14159265/(2*(maxTerms+1))));
	    double newomegat = omega[i] * t;
	    phasecoefadj[i] = (phasecoefadj[i] + oldomegat-newomegat) % (2*pi);
	}
    }

    void setLoadCount() {
	sampleCount = maxTerms = loadBar.getValue();
	step = pi/sampleCount;
	int x, y;
	sinTable = new double[sampleCount+1][maxTerms];
	for (y = 1; y != maxTerms; y++)
	    for (x = 0; x != sampleCount+1; x++)
		sinTable[x][y] = java.lang.Math.sin(step*x*y);
	omega = new double[maxTerms];
	int i;
	for (i = 1; i != maxTerms; i++)
	    omega[i] = java.lang.Math.sin(i*(3.14159265/(2*(maxTerms+1))));
	double mult = 1/omega[1];
	for (i = 1; i != maxTerms; i++)
	    omega[i] *= mult;
	setDamping();
    }

    void setDamping() {
	int i;
	double damper = java.lang.Math.exp(dampingBar.getValue()/40-8);
	if (dampingBar.getValue() <= 2)
	    damper = 0;
	dampcoef = -damper;
    }

    public void mouseDragged(MouseEvent e) {
	dragging = true;
	edit(e);
    }
    public void mouseMoved(MouseEvent e) {
	if ((e.getModifiers() & MouseEvent.BUTTON1_MASK) != 0)
	    return;
	int x = e.getX();
	int y = e.getY();
	dragX = x; dragY = y;
	int panelHeight = getPanelHeight();
	int oldCoef = selectedCoef;
	selectedCoef = -1;
	selection = 0;
	if (y < panelHeight)
	    selection = SEL_FUNC;
	if (y >= magnitudesY && y < magnitudesY+panelHeight) {
	    int termWidth = getTermWidth();
	    selectedCoef = x/termWidth+1;
	    if (selectedCoef >= maxTerms)
		selectedCoef = -1;
	    if (selectedCoef != -1)
		selection = SEL_MAG;
	}
	if (selectedCoef != oldCoef)
	    cv.repaint(pause);
    }
    public void mouseClicked(MouseEvent e) {
	if (e.getClickCount() == 2 && selectedCoef != -1) {
	    int i;
	    for (i = 1; i != maxTerms; i++)
		if (selectedCoef != i)
		    magcoef[i] = 0;
	    magcoef[selectedCoef] = 1;
	    updateSound();
	    cv.repaint(pause);
	}
    }
    public void mouseEntered(MouseEvent e) {
    }
    public void mouseExited(MouseEvent e) {
	if (!dragging && selectedCoef != -1) {
	    selectedCoef = -1;
	    cv.repaint(pause);
	}
    }
    public void mousePressed(MouseEvent e) {
	if ((e.getModifiers() & MouseEvent.BUTTON1_MASK) == 0)
	    return;
	if (selection == SEL_FUNC)
	    getVelocities();
	dragging = true;
	edit(e);
    }
    public void mouseReleased(MouseEvent e) {
	if ((e.getModifiers() & MouseEvent.BUTTON1_MASK) == 0)
	    return;
	if (forceApplied || bowing) {
	    bowing = bowCaught = false;
	    forceAppliedOff();
	} else if (dragging && selection == SEL_FUNC)
	    transform(false);
	dragging = false;
	cv.repaint(pause);
    }
    public void itemStateChanged(ItemEvent e) {
	if (e.getItemSelectable() == stoppedCheck) {
	    cv.repaint(pause);
	    return;
	}
	if (e.getItemSelectable() == forceCheck) {
	    forceTimeZero = t;
	    cv.repaint(pause);
	    forceAppliedOff();
	    soundCheck.setState(false);
	    return;
	}
	if (e.getItemSelectable() == soundCheck && soundCheck.getState() &&
	    playThread == null) {
	    playThread = new PlayThread();
	    speedBar.setValue(150);
	    dampingBar.setValue(100);
	    setDamping();
	    playThread.start();
	}
	if (e.getItemSelectable() == displayChooser)
	    cv.repaint(pause);
    }

    class PlayThread extends Thread {
	public void soundChanged() { changed = true; }
	boolean changed;
	final int rate = 22050;
	public void run() {
	    try {
		doRun();
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	    playThread = null;
	    soundCheck.setState(false);
	}
	void doRun() {
	    
	    // this lovely code is a translation of the following, using
	    // reflection, so we can run on JDK 1.1:
	    
	    // AudioFormat format = new AudioFormat(rate, 8, 1, true, true);
	    // DataLine.Info info =
	    //           new DataLine.Info(SourceDataLine.class, format);
	    // SourceDataLine line = null;
	    // line = (SourceDataLine) AudioSystem.getLine(info);
	    // line.open(format, playSampleCount);
	    // line.start();

	    Object line;
	    Method wrmeth = null;
	    try {
		Class afclass = Class.forName("javax.sound.sampled.AudioFormat");
		Constructor cstr = afclass.getConstructor(
		    new Class[] { float.class, int.class, int.class,
				  boolean.class, boolean.class });
		Object format = cstr.newInstance(new Object[]
		    { new Float(rate), new Integer(16), new Integer(1),
		      new Boolean(true), new Boolean(true) });
		Class ifclass = Class.forName("javax.sound.sampled.DataLine$Info");
		Class sdlclass =
		    Class.forName("javax.sound.sampled.SourceDataLine");
		cstr = ifclass.getConstructor(
		    new Class[] { Class.class, afclass });
		Object info = cstr.newInstance(new Object[]
		    { sdlclass, format });
		Class asclass = Class.forName("javax.sound.sampled.AudioSystem");
		Class liclass = Class.forName("javax.sound.sampled.Line$Info");
		Method glmeth = asclass.getMethod("getLine",
						  new Class[] { liclass });
		line = glmeth.invoke(null, new Object[] {info} );
		Method opmeth = sdlclass.getMethod("open",
			  new Class[] { afclass, int.class });
		opmeth.invoke(line, new Object[] { format,
			  new Integer(4096) });
		Method stmeth = sdlclass.getMethod("start", null);
		stmeth.invoke(line, null);
		byte b[] = new byte[1];
		wrmeth = sdlclass.getMethod("write",
			  new Class[] { b.getClass(), int.class, int.class });
	    } catch (Exception e) {
		e.printStackTrace();
		return;
	    }

	    int playSampleCount = 16384;
	    FFT playFFT = new FFT(playSampleCount);
	    double playfunc[] = null;
	    byte b[] = new byte[4096];
	    int offset = 0;
	    int dampCount = 0;
	    double mx = .2;
		    
	    while (soundCheck.getState() && applet.ogf != null) {
		double damper = dampcoef*1e-2;
		
		if (playfunc == null || changed) {
		    playfunc = new double[playSampleCount*2];
		    int i;
		    //double bstep = 2*pi*440./rate;
		    //int dfreq0 = 440; // XXX
		    double n = 2*pi*20.0*
			java.lang.Math.sqrt((double)tensionBarValue);
		    n /= omega[1];
		    changed = false;
		    mx = .2;
		    for (i = 1; i != maxTerms; i++) {
			int dfreq = (int) (n*omega[i]);
			if (dfreq >= playSampleCount)
			    break;
			playfunc[dfreq] = magcoef[i];
		    }
		    playFFT.transform(playfunc, true);
		    for (i = 0; i != playSampleCount; i++) {
			double dy = playfunc[i*2]*Math.exp(damper*i);
			if (dy > mx)  mx = dy;
			if (dy < -mx) mx = -dy;
		    }
		    dampCount = offset = 0;
		}
		
		double mult = 32767/mx;
		int bl = b.length/2;
		int i;
		for (i = 0; i != bl; i++) {
		    short x = (short) (playfunc[(i+offset)*2]*mult*
				       Math.exp(damper*dampCount++));
		    b[i*2] = (byte) (x/256);
		    b[i*2+1] = (byte) (x & 255);
		}
		offset += bl;
		if (offset == playfunc.length/2)
		    offset = 0;

		try {
		    wrmeth.invoke(line, new Object[] { b, new Integer(0),
						       new Integer(b.length) });
		} catch (Exception e) {
		    e.printStackTrace();
		    break;
		}
	    }
	}
    }
}

class FFT {
    double wtabf[];
    double wtabi[];
    int size;
    FFT(int sz) {
	size = sz;
	if ((size & (size-1)) != 0)
	    System.out.println("size must be power of two!");
	calcWTable();
    }
    
    void calcWTable() {
	// calculate table of powers of w
	wtabf = new double[size];
	wtabi = new double[size];
	int i;
	for (i = 0; i != size; i += 2) {
	    double pi = 3.1415926535;
	    double th = pi*i/size;
	    wtabf[i  ] = (double)Math.cos(th);
	    wtabf[i+1] = (double)Math.sin(th);
	    wtabi[i  ] = wtabf[i];
	    wtabi[i+1] = -wtabf[i+1];
	}
    }
    
    void transform(double data[], boolean inv) {
	int i;
	int j = 0;
	int size2 = size*2;

	if ((size & (size-1)) != 0)
	    System.out.println("size must be power of two!");
	
	// bit-reversal
	double q;
	int bit;
	for (i = 0; i != size2; i += 2) {
	    if (i > j) {
		q = data[i]; data[i] = data[j]; data[j] = q;
		q = data[i+1]; data[i+1] = data[j+1]; data[j+1] = q;
	    }
	    // increment j by one, from the left side (bit-reversed)
	    bit = size;
	    while ((bit & j) != 0) {
		j &= ~bit;
		bit >>= 1;
	    }
	    j |= bit;
	}

	// amount to skip through w table
	int tabskip = size << 1;
	double wtab[] = (inv) ? wtabi : wtabf;
	
	int skip1, skip2, ix, j2;
	double wr, wi, d1r, d1i, d2r, d2i, d2wr, d2wi;
	
	// unroll the first iteration of the main loop
	for (i = 0; i != size2; i += 4) {
	    d1r = data[i];
	    d1i = data[i+1];
	    d2r = data[i+2];
	    d2i = data[i+3];
	    data[i  ] = d1r+d2r;
	    data[i+1] = d1i+d2i;
	    data[i+2] = d1r-d2r;
	    data[i+3] = d1i-d2i;
	}
	tabskip >>= 1;
	
	// unroll the second iteration of the main loop
	int imult = (inv) ? -1 : 1;
	for (i = 0; i != size2; i += 8) {
	    d1r = data[i];
	    d1i = data[i+1];
	    d2r = data[i+4];
	    d2i = data[i+5];
	    data[i  ] = d1r+d2r;
	    data[i+1] = d1i+d2i;
	    data[i+4] = d1r-d2r;
	    data[i+5] = d1i-d2i;
	    d1r = data[i+2];
	    d1i = data[i+3];
	    d2r = data[i+6]*imult;
	    d2i = data[i+7]*imult;
	    data[i+2] = d1r-d2i;
	    data[i+3] = d1i+d2r;
	    data[i+6] = d1r+d2i;
	    data[i+7] = d1i-d2r;
	}
	tabskip >>= 1;
	
	for (skip1 = 16; skip1 <= size2; skip1 <<= 1) {
	    // skip2 = length of subarrays we are combining
	    // skip1 = length of subarray after combination
	    skip2 = skip1 >> 1;
	    tabskip >>= 1;
	    for (i = 0; i != 1000; i++);
	    // for each subarray
	    for (i = 0; i < size2; i += skip1) {
		ix = 0;
		// for each pair of complex numbers (one in each subarray)
		for (j = i; j != i+skip2; j += 2, ix += tabskip) {
		    wr = wtab[ix];
		    wi = wtab[ix+1];
		    d1r = data[j];
		    d1i = data[j+1];
		    j2 = j+skip2;
		    d2r = data[j2];
		    d2i = data[j2+1];
		    d2wr = d2r*wr - d2i*wi;
		    d2wi = d2r*wi + d2i*wr;
		    data[j]    = d1r+d2wr;
		    data[j+1]  = d1i+d2wi;
		    data[j2  ] = d1r-d2wr;
		    data[j2+1] = d1i-d2wi;
		}
	    }
	}
    }

}
