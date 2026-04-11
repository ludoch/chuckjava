import os

mapping = {
    'osc': ['Blit.java', 'BlitSaw.java', 'BlitSquare.java', 'CNoise.java', 'Gen10.java', 'Gen17.java', 'Gen5.java', 'Gen7.java', 'Gen9.java', 'GenX.java', 'Noise.java', 'Osc.java', 'Phasor.java', 'PulseOsc.java', 'SawOsc.java', 'SineWave.java', 'SinOsc.java', 'SqrOsc.java', 'SubNoise.java', 'TriOsc.java', 'WaveLoop.java'],
    'filter': ['AllPass.java', 'BiQuad.java', 'BiQuadStk.java', 'BPF.java', 'BRF.java', 'FilterBasic.java', 'FilterStk.java', 'HPF.java', 'Lpf.java', 'OnePole.java', 'OneZero.java', 'PoleZero.java', 'ResonZ.java', 'TwoPole.java', 'TwoZero.java'],
    'fx': ['Chorus.java', 'Comb.java', 'Delay.java', 'DelayA.java', 'DelayL.java', 'DelayP.java', 'Dyno.java', 'Echo.java', 'GVerb.java', 'JCRev.java', 'NRev.java', 'PitShift.java', 'PRCRev.java'],
    'stk': ['BandedWG.java', 'BeeThree.java', 'BlowBotl.java', 'BlowHole.java', 'Bowed.java', 'Brass.java', 'Clarinet.java', 'Flute.java', 'FMVoices.java', 'FrencHrn.java', 'HevyMetl.java', 'HnkyTonk.java', 'KrstlChr.java', 'Mandolin.java', 'Mesh2D.java', 'ModalBar.java', 'Moog.java', 'PercFlut.java', 'Plucked.java', 'Rhodey.java', 'Saxofony.java', 'Shakers.java', 'Sitar.java', 'StifKarp.java', 'TubeBell.java', 'VoicForm.java', 'Wurley.java'],
    'analysis': ['AutoCorr.java', 'Centroid.java', 'Chroma.java', 'DCT.java', 'FeatureCollector.java', 'FFT.java', 'Flip.java', 'Flux.java', 'IDCT.java', 'IFFT.java', 'Kurtosis.java', 'MFCC.java', 'RMS.java', 'Rolloff.java', 'SFM.java', 'UnFlip.java', 'XCorr.java', 'ZCR.java', 'ZeroX.java'],
    'util': ['Adc.java', 'Adsr.java', 'Blackhole.java', 'BowTable.java', 'ChannelProxy.java', 'Complex.java', 'CurveTable.java', 'DacChannel.java', 'Envelope.java', 'FullRect.java', 'Gain.java', 'GainDB.java', 'HalfRect.java', 'Identity2.java', 'Impulse.java', 'JetTabl.java', 'LiSa.java', 'LiSa2.java', 'LiSaN.java', 'Mix16.java', 'Mix2.java', 'Mix4.java', 'Mix8.java', 'MixN.java', 'Modulate.java', 'MultiChannelDac.java', 'MultiChannelUGen.java', 'Pan16.java', 'Pan2.java', 'Pan4.java', 'Pan8.java', 'PanN.java', 'ReedTable.java', 'Scope.java', 'SndBuf.java', 'SndBuf2.java', 'Step.java', 'StereoUGen.java', 'Teabox.java', 'WarpTable.java', 'WvIn.java', 'WvOut.java', 'WvOut2.java', 'WvOutUGen.java']
}

base_dir = 'src/main/java/org/chuck/audio'

for subpkg, files in mapping.items():
    for filename in files:
        old_path = os.path.join(base_dir, filename)
        new_path = os.path.join(base_dir, subpkg, filename)
        
        if os.path.exists(old_path):
            with open(old_path, 'r') as f:
                content = f.read()
            
            new_content = content.replace('package org.chuck.audio;', f'package org.chuck.audio.{subpkg};')
            
            with open(new_path, 'w') as f:
                f.write(new_content)
            
            os.remove(old_path)
            print(f'Moved {filename} to {subpkg}')
        else:
            print(f'Warning: {filename} not found at {old_path}')
