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
		1: [\delaytime, {|x| x*2/127}],
		5: [\decaytime, {|x| x*8/127 }]
	),
	graphFunc:	{
		|dryBus, effectBus, delaytime, decaytime|
		var dry = In.ar(dryBus, 2);
		var audio = dry + CombC.ar(in:dry,maxdelaytime:4,delaytime:delaytime,decaytime:decaytime);
		Out.ar(effectBus, audio);
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
		4: [\room, {|x| x/127}],
		8: [\size, {|x| pow(x/127,2)}]
	),
	graphFunc:
	{
		|dryBus, effectBus, room=0, size=0|
		var in, snd, loop, depth;
		var dry = In.ar(dryBus, 2);

		in = dry.asArray.sum;

		in = in * room.lag(LFNoise1.kr(1).range(0.01, 0.02)); // regulate input

		4.do { in = AllpassN.ar(in, 0.03, { Rand(0.005, 0.02) }.dup(2), 1) };

		depth = size.lag(0.02).linexp(0, 1, 0.01, 0.98); // change depth between 0.1 and 0.98
		loop = LocalIn.ar(2) * { depth + Rand(0, 0.05) }.dup(2);
		loop = OnePole.ar(loop, 0.5);  // 0-1

		loop = AllpassN.ar(loop, 0.05, { Rand(0.01, 0.05) }.dup(2), 2);

		loop = DelayN.ar(loop, 0.3, [0.19, 0.26] + { Rand(-0.003, 0.003) }.dup(2));
		loop = AllpassN.ar(loop, 0.05, { Rand(0.03, 0.15) }.dup(2), 2);

		loop = loop + in;
		loop = LeakDC.ar(loop);

		LocalOut.ar(loop);

		snd = loop;
		snd = dry + snd * (1).lag(LFNoise1.kr(1).range(0.01, 0.02));

		Out.ar(effectBus, snd);
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