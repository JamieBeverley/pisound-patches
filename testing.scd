/*

on/off  on/off  on/off   on/off       room  delytime phaserrate   distortion
loop    loop    sample1  sample 2     size  delayfb  phaserdepth  distortiontone


*/
().play
(
{
	Out.ar(0, In.ar(2,numChannels:2));
}.play()
)


MIDIClient.init
MIDIIn.connectAll
Server.default.boot
(
var delay = PiSoundEffect(
	name: 'delay',
	toggleMidiNote:48,
	knobs:(
		1: [\delay, {|x| x*2/127}],
		5: [\delaytime, {|x| x*2/127 }]
	),
	graphFunc:	{
		|dryBus, effectBus, delay=0, delaytime=0.5|

		var dry, signal;
		var maxDelayTime = 4;
		var decayTime;
		var delayfeedback = delay;
		delaytime = delaytime.linlin(0,1,0.1,1);
		dry = In.ar(in, 2);

		// Delay
		signal = dry;
		delayfeedback = delayfeedback.clip(0, 0.99);
		delaytime = delaytime * if(lock, reciprocal(cps), 1);
		delaytime = delaytime.clip(0, maxDelayTime); // just to be sure
		decayTime = log2(-60.dbamp) / log2(delayfeedback) * delaytime;
		decayTime = decayTime.clip(0, 20);

		signal = CombL.ar(signal, maxDelayTime, delaytime.lag(1), decayTime);

		signal = LeakDC.ar(signal) * delay.lag(0.01);
		signal = dry + signal;
		Out.ar(effectBus, signal);
	}
);

var phaser = PiSoundEffect(
	name: 'phaser',
	toggleMidiNote:49,
	knobs:(
		2: [\phaserrate, {|x| x*8/127}],
		6: [\phaserdepth, {|x| x*4/127 }]
	),
	graphFunc:	{
		|dryBus, effectBus, phaserrate = 1.0, phaserdepth = 0.5 |
		var signal, phaserwave;
		var phase =  LFPar.kr(phaserrate).range(0.0088, 0.01);

		phaserdepth = phaserdepth.clip(0, 1);

		signal = In.ar(dryBus, 2);

		phaserwave = AllpassL.ar(signal, 8, phase, 0, phaserdepth);

		signal = signal + phaserwave;
		Out.ar(effectBus, signal);
	}
);

var distortion = PiSoundEffect(
	name: 'tremolo',
	toggleMidiNote:50,
	knobs:(
		3: [\tremolorate, {|x| x*16/127}],
		7: [\tremolodepth, {|x| x/127}]
	),
	graphFunc: {
		|dryBus, effectBus, tremolorate = 1.0, tremolodepth = 0.5 |
		var signal, tremolowave;

		tremolodepth = tremolodepth.clip(0, 1) * 0.5;
		tremolowave = (1 - tremolodepth) + SinOsc.ar(tremolorate, 0.5pi, tremolodepth);

		signal = In.ar(dryBus, 2);
		signal = signal * tremolowave;
		Out.ar(effectBus, signal);
	}
);

var reverb = PiSoundEffect(
	name:'reverb',
	toggleMidiNote:51,
	knobs:(
		4: [\reverb, {|x| pow(x/127, 3)}],
	),
	graphFunc:
	{
		|dryBus, effectBus, reverb=0|
		var z,y;
		var dry = In.ar(dryBus, 2);

		// Reverb
		z = DelayN.ar(dry, ((0.048)));
		y = Mix.ar(Array.fill(7,{ CombL.ar(z, 0.1, LFNoise1.kr(0.1.rand, 0.04, 0.05), 15) }));
		4.do({ y = AllpassN.ar(y, 0.050*size, [0.050.rand, 0.050.rand], 1) });
		audio = dry+(reverb*y);
		Out.ar(effectBus, audio);
	}
);

~a = PiSoundModule(\pisound,[delay, phaser, reverb, distortion],2);
)


a = Synth.new
~a.outSynth.set(\dryBus, 12);
~a.inSynth.set(\in, 2
Synth
~a.outSynth

~a.outSynth.isPlaying

~a.inSynth.isPlaying

~a.outSynth.isPlaying

{Out.ar(0,SinOsc.ar()*0.1)}.play

PiSoundModule.start