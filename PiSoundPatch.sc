/*
on/off midinote/chan
on/off midinote out

list of knobs: chan/note

graph func
test

*/

PiSoundEffect : Object{

	var <>toggleMidiNote;
	var <>knobs;
	var <>name;
	/*
	(add t
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
	var <>verbose;

	*new {
		|name, toggleMidiNote, knobs, graphFunc, verbose=false|
		var a = super.new(name, toggleMidiNote, knobs, graphFunc, verbose);
		^a.initPiSoundEffect(name, toggleMidiNote, knobs, graphFunc, verbose);
	}

	initPiSoundEffect {
		|name,toggleMidiNote, knobs, graphFunc, verbose|
		this.name = name;
		this.toggleMidiNote = toggleMidiNote;
		this.knobs = knobs;
		this.graphFunc = graphFunc;
		this.bus = Bus.audio(Server.default, 2);
		this.on = false;
		this.verbose = verbose;
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
			if(this.verbose,{[paramName, value].postln});
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
					1: [\delay, {|x| x*2/127}],
					5: [\delaytime, {|x| x*2/127 }]
				),
				graphFunc:	{
					|dryBus, effectBus, delay=0, delaytime=0.5, lock=0, cps=1|

					var dry, signal;
					var maxDelayTime = 4;
					var decayTime;
					var delayfeedback = delay;
					delaytime = delaytime.linlin(0,1,0.1,1);
					dry = In.ar(dryBus, 2);

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

			trem = PiSoundEffect(
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
					4: [\reverb, {|x| pow(x/127, 3)}],
				),
				graphFunc:
				{
					|dryBus, effectBus, reverb=0|
					var z,y,audio;
					var dry = In.ar(dryBus, 2);

					// Reverb
					z = DelayN.ar(dry, ((0.048)));
					y = Mix.ar(Array.fill(7,{ CombL.ar(z, 0.1, LFNoise1.kr(0.1.rand, 0.04, 0.05), 15) }));
					4.do({ y = AllpassN.ar(y, 0.050, [0.050.rand, 0.050.rand], 1) });
					audio = dry+(reverb*y);
					Out.ar(effectBus, audio);
				}
			);
			~a = PiSoundModule(\pisound,[delay, phaser, reverb, trem],2);

		}).play;
	}
}
