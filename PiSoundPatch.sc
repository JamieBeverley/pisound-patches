/*
on/off midinote/chan
on/off midinote out

list of knobs: chan/note

graph func


*/

PiSoundEffect : Object{

	var <>toggleMidiNote;
	var <>knobs;
	var <>name;
	/*
	(
	21: [\gain, {|x| x*2/127}],
	22: [\attack, {|x| x*2/127 }],
	23: [\decay, {|x| x/127 }],
	24: [\sustainLevel, {|x| x/127 }],
	25: [\release, {|x| x*4/127 }],
	26: [\lpf, {|x| ((x/127).pow(3)*22000) }],
	27: [\lpfq, {|x| (1-(x/127)).clip(0.05,1)}],
	28: [\delayTime, {|x| (x/127)}]
	);
	*/
	var <>graphFunc;
	var <>on;
	var <>bus;
	var <>midiNote;
	var <>midiCC;
	var <>synth;

	*new {
		|name, toggleMidiNote, knobs, graphFunc|
		var a = super.new(name, toggleMidiNote, knobs, graphFunc);
		^a.initPiSoundEffect(name, toggleMidiNote, knobs, graphFunc);
	}

	initPiSoundEffect {
		|name,toggleMidiNote, knobs, graphFunc|
		this.name = name;
		this.toggleMidiNote = toggleMidiNote;
		this.knobs = knobs;
		this.graphFunc = graphFunc;
		this.bus = Bus.audio(Server.default, 2);
		this.on = false;
		SynthDef(this.name, this.graphFunc).add;
	}

	initSynth {
		|after|
		this.synth = Synth.after(after, this.name, args:[\effectBus, this.bus]);
		^this.synth;
	}

	initMidiCC{
		this.midiCC = MIDIFunc.cc({
			|val,num,chan,src|
			var spec = this.knobs[num];
			var paramName = spec[0];
			var value = spec[1].value(val);
			[paramName, value].postln;
			if(isNil(this.synth).not,this.synth.set(paramName, value));
		},ccNum:this.knobs.keys);
	}
}

PiSoundModule : Object{

	var <>inSynth;
	var <>outSynth;
	var <>dryBus;
	var <>effects;
	var <>in;
	var <>midiNoteFunc;
	var <>name;
	var <>controllerMidiOut;

	*new{
		|name=\piSound, effects, in=0|
		var a = super.new(name, effects, in);
		^a.initPiSoundModule(name, effects, in);
	}

	*getLPD8{
		var lpd8;
		MIDIClient.destinations.do{|x,i| if(x.name.contains("LPD8"),{lpd8=x})};
		^MIDIOut(port:0, uid:lpd8.uid);
	}

	loadCoreSynths{
		SynthDef(this.name++'_pisound_in', {
			var dry = In.ar(this.in, 2);
			Out.ar(this.dryBus, dry);
		}).add;

		SynthDef(this.name++'_pisound_out', {
			|dryBus|
			var dry = In.ar(dryBus, 2);
			Out.ar(0, dry);
		}).add;
	}


	initPiSoundModule{
		|name=\piSound, effects,in|
		// var lastSynth = effects[effects.size-1].synth;
		// var firstSynth = effects[0].synth;
		var inName = name++'_pisound_in';
		var outName = name++'_pisound_out';
		this.in = in;
		this.name = name;
		this.dryBus = Bus.audio(Server.default, 2);
		this.loadCoreSynths();
		this.effects = effects;
		this.controllerMidiOut = PiSoundModule.getLPD8();
		Routine({
			var lastSynth;
			var lastOut;
			1.wait;

			this.inSynth = Synth.new(inName);
			lastSynth = this.inSynth;
			lastOut = this.dryBus;
			this.effects.do{
				|effect|
				lastSynth = effect.initSynth(lastSynth);
				lastOut = effect.bus;
			};
			this.outSynth = Synth.after(lastSynth, outName, args:[\dryBus, lastOut]);
			this.setSynthOrder();
			this.initMidi();
			"done".postln;
		}).play;
	}

	initMidi {
		this.effects.do{
			|effect|
			effect.initMidiCC();
		};
		this.midiNoteFunc = MIDIFunc.noteOn({
			|val,num,chan,src|
			var update = false;
			this.effects.do{
				|effect|
				if(effect.toggleMidiNote == num,{
					effect.on = effect.on.not;
					effect.on.postln;
					this.controllerMidiOut.noteOn(chan:0,note:num,veloc:if(effect.on,{1},{0}));
					update = true;
				});
			};
			if(update, {this.setSynthOrder()});
		});
	}

	setSynthOrder{
		var lastOut = this.dryBus;
		["dry", dryBus.index].postln;

		this.effects.do{
			|effect|
			if(effect.on,{
				effect.synth.set(\dryBus, lastOut);
				[effect.name, lastOut.index, effect.bus.index].postln;
				lastOut = effect.bus;
			});
		};
		["lastOut", lastOut.index].postln;
		this.outSynth.set(\dryBus, lastOut);
	}


	*start{
		|waitTime=8|
		Routine({
			var delay,phaser,trem, reverb;
			Server.default.boot;
			MIDIClient.init;
			MIDIIn.connectAll;
			waitTime.wait;
			delay = PiSoundEffect(
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

			phaser = PiSoundEffect(
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

			trem= PiSoundEffect(
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

			reverb = PiSoundEffect(
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

			~a = PiSoundModule(\pisound,[delay, phaser, reverb, trem],2);

		}).play;
	}
}
