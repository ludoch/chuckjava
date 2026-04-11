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
subpkgs = ['osc', 'filter', 'fx', 'stk', 'analysis', 'util']

base_dir = 'src/main/java/org/chuck/audio'

for current_subpkg in subpkgs:
    pkg_dir = os.path.join(base_dir, current_subpkg)
    if not os.path.exists(pkg_dir):
        continue
        
    for filename in os.listdir(pkg_dir):
        if filename.endswith('.java'):
            file_path = os.path.join(pkg_dir, filename)
            with open(file_path, 'r') as f:
                lines = f.readlines()
            
            content = "".join(lines)
            package_line_index = -1
            for i, line in enumerate(lines):
                if line.startswith('package '):
                    package_line_index = i
                    break
            
            needed_imports = []
            
            # Check for other moved classes
            for class_name, subpkg in class_to_subpkg.items():
                if subpkg == current_subpkg:
                    continue # same package, no import needed
                
                if re.search(r'\b' + class_name + r'\b', content):
                    # Class is used, need import
                    import_stmt = f'import org.chuck.audio.{subpkg}.{class_name};'
                    if import_stmt not in content:
                        needed_imports.append(import_stmt)

            if needed_imports:
                # Insert imports after package declaration (and after any core imports added before)
                # Let's just find the last import or the package line
                insert_pos = package_line_index + 1
                while insert_pos < len(lines) and (lines[insert_pos].strip() == "" or lines[insert_pos].startswith('import ')):
                    insert_pos += 1
                
                # To be safe, let's just insert right after the package line or the core imports
                new_lines = lines[:package_line_index+1]
                # Check if we already have a newline after package
                if package_line_index + 1 < len(lines) and lines[package_line_index+1].strip() != "":
                     new_lines.append('\n')
                
                # Filter out already existing imports in needed_imports if any
                needed_imports = [imp for imp in needed_imports if imp not in content]
                
                if needed_imports:
                    for imp in sorted(needed_imports):
                        new_lines.append(imp + '\n')
                    
                    new_lines.extend(lines[package_line_index+1:])
                    
                    with open(file_path, 'w') as f:
                        f.writelines(new_lines)
                    print(f"Added cross-subpkg imports to {file_path}")
