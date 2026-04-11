import os
import re

mapping = {
    'osc': ['Blit.java', 'BlitSaw.java', 'BlitSquare.java', 'CNoise.java', 'Gen10.java', 'Gen17.java', 'Gen5.java', 'Gen7.java', 'Gen9.java', 'GenX.java', 'Noise.java', 'Osc.java', 'Phasor.java', 'PulseOsc.java', 'SawOsc.java', 'SineWave.java', 'SinOsc.java', 'SqrOsc.java', 'SubNoise.java', 'TriOsc.java', 'WaveLoop.java'],
    'filter': ['AllPass.java', 'BiQuad.java', 'BiQuadStk.java', 'BPF.java', 'BRF.java', 'FilterBasic.java', 'FilterStk.java', 'HPF.java', 'Lpf.java', 'OnePole.java', 'OneZero.java', 'PoleZero.java', 'ResonZ.java', 'TwoPole.java', 'TwoZero.java'],
    'fx': ['Chorus.java', 'Comb.java', 'Delay.java', 'DelayA.java', 'DelayL.java', 'DelayP.java', 'Dyno.java', 'Echo.java', 'GVerb.java', 'JCRev.java', 'NRev.java', 'PitShift.java', 'PRCRev.java'],
    'stk': ['BandedWG.java', 'BeeThree.java', 'BlowBotl.java', 'BlowHole.java', 'Bowed.java', 'Brass.java', 'Clarinet.java', 'Flute.java', 'FMVoices.java', 'FrencHrn.java', 'HevyMetl.java', 'HnkyTonk.java', 'KrstlChr.java', 'Mandolin.java', 'Mesh2D.java', 'ModalBar.java', 'Moog.java', 'PercFlut.java', 'Plucked.java', 'Rhodey.java', 'Saxofony.java', 'Shakers.java', 'Sitar.java', 'StifKarp.java', 'TubeBell.java', 'VoicForm.java', 'Wurley.java'],
    'analysis': ['AutoCorr.java', 'Centroid.java', 'Chroma.java', 'DCT.java', 'FeatureCollector.java', 'FFT.java', 'Flip.java', 'Flux.java', 'IDCT.java', 'IFFT.java', 'Kurtosis.java', 'MFCC.java', 'RMS.java', 'Rolloff.java', 'SFM.java', 'UnFlip.java', 'XCorr.java', 'ZCR.java', 'ZeroX.java'],
    'util': ['Adc.java', 'Adsr.java', 'Blackhole.java', 'BowTable.java', 'ChannelProxy.java', 'Complex.java', 'CurveTable.java', 'DacChannel.java', 'Envelope.java', 'FullRect.java', 'Gain.java', 'GainDB.java', 'HalfRect.java', 'Identity2.java', 'Impulse.java', 'JetTabl.java', 'LiSa.java', 'LiSa2.java', 'LiSaN.java', 'Mix16.java', 'Mix2.java', 'Mix4.java', 'Mix8.java', 'MixN.java', 'Modulate.java', 'MultiChannelDac.java', 'MultiChannelUGen.java', 'Pan16.java', 'Pan2.java', 'Pan4.java', 'Pan8.java', 'PanN.java', 'ReedTable.java', 'Scope.java', 'SndBuf.java', 'SndBuf2.java', 'Step.java', 'StereoUGen.java', 'Teabox.java', 'WarpTable.java', 'WvIn.java', 'WvOut.java', 'WvOut2.java', 'WvOutUGen.java']
}

class_to_subpkg = {}
for subpkg, files in mapping.items():
    for f in files:
        class_name = f.replace('.java', '')
        class_to_subpkg[class_name] = subpkg

core_classes = ['ChuckAudio', 'ChuckUGen', 'ChuGen', 'Chugraph', 'UAna', 'UAnaBlob', 'VectorAudio']

def update_file(file_path):
    with open(file_path, 'r') as f:
        content = f.read()

    new_content = content
    
    # Replace individual imports
    for class_name, subpkg in class_to_subpkg.items():
        old_import = f'import org.chuck.audio.{class_name};'
        new_import = f'import org.chuck.audio.{subpkg}.{class_name};'
        new_content = new_content.replace(old_import, new_import)

    # Replace wildcard imports
    wildcard_pattern = r'import org\.chuck\.audio\.\*;'
    if re.search(wildcard_pattern, new_content):
        new_imports = "import org.chuck.audio.*;\n"
        new_imports += "import org.chuck.audio.analysis.*;\n"
        new_imports += "import org.chuck.audio.filter.*;\n"
        new_imports += "import org.chuck.audio.fx.*;\n"
        new_imports += "import org.chuck.audio.osc.*;\n"
        new_imports += "import org.chuck.audio.stk.*;\n"
        new_imports += "import org.chuck.audio.util.*;"
        new_content = re.sub(wildcard_pattern, new_imports, new_content)

    if new_content != content:
        with open(file_path, 'w') as f:
            f.write(new_content)
        print(f"Updated {file_path}")

# Traverse all java files in src and examples_dsl
for root, dirs, files in os.walk('.'):
    if 'src' in root or 'examples_dsl' in root:
        for file in files:
            if file.endswith('.java'):
                update_file(os.path.join(root, file))
