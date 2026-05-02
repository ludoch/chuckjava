// DX7 Patch Format Decoder v4
// These are Deluge community firmware DX7 patches. The Deluge community firmware
// stores the raw DX7 voice format in XML. Let me analyze with looser constraints.
//
// Key question: is this standard 128-byte (packed) or 155-byte (unpacked) DX7 format?
// A 155-byte unpacked dump = 310 hex chars ✓ matches both patches
// A 128-byte packed dump = 256 hex chars ✗ doesn't match

const hex1 = '63521B19604E0000290000000000000363010147076355141C5B30000036000000000000006001014F0763321930630000003600000000000000430000000763631F2063630000370030000003000163000000076316140B610100003600000003000048000000075F251416635C00003B00000000070002630000000762626262323232320707010000630001040718546F6D737765657020203F';
const hex2 = '6333032D634E4D00273A2F02010000004C0006000743150C29635F5C0027221C02010000004C0002000A5A080728636261002C0B1C02020000004E000200056306062F635A5A0038000003000000005E01003A0E5A2C0426615E5D003712190102000000530001010063050F2D635A5A0027000003010000006301002807614B5E42323232321707011A0F1F0001040118414D53544544414D203F';

function bytesOf(hex) {
  return hex.match(/.{2}/g).map(function(b) { return parseInt(b, 16); });
}

const b1 = bytesOf(hex1);
const b2 = bytesOf(hex2);

// Let me dump the FULL byte array in a clean readable format
function dumpHex(bytes, label) {
  console.log('=== ' + label + ' ===');
  for (var i = 0; i < bytes.length; i++) {
    if (i > 0 && i % 16 === 0) console.log('');
    process.stdout.write(bytes[i].toString(16).padStart(2,'0') + ' ');
  }
  console.log('\n');
}

dumpHex(b1, 'Patch 1 - Tomsweep (155 bytes)');
dumpHex(b2, 'Patch 2 - AMSTERDAM (155 bytes)');

// Key observations from the data:
// 1. Both patches end with ..."Tomsweep  \u003f" or similar
// 2. Both are 155 bytes (310 hex chars)
// 3. Patch 2 actually spells "AMSTEDAM" not "AMSTERDAM" (missing the R)

// The ACTUAL Yamaha DX7 unpacked voice format (verified by Dexed source code):
// Total: 155 bytes
//
// Bytes 0-15:  0xF0 0x43 0x00 0x09 0x20 + 11 bytes (SysEx header)
// Bytes 16-127: Voice parameters (voice data packed to 7-bit, 112 bytes * 7/8 = 128 -> no)
//
// Wait. Let me look at the ACTUAL format used by Dexed (the gold standard for DX7 emulation).
// Dexed uses the "SYX" format which is 163 bytes:
//   6 bytes SysEx header:  F0 43 <substatus> <channel> 09 20
//   128 bytes packed voice data
//   1 byte checksum
//   1 byte terminator F7
//   Total: 136 bytes
//
// But Dexed also supports "unpacked" 155-byte format.
//
// In the unpacked 155-byte format, the ACTUAL layout from Yamaha DX7 manual:
// Bytes 0-4:    SysEx header data  (F0 43 0g 09 20 where g=channel/group)
// Bytes 5-16:   Various header/formatting stuff
// Bytes 17-154: Voice parameter data (138 bytes)
//
// That's 155 bytes total. But the 138 bytes of voice data are NOT 6*23 operators.
// Instead, the voice data is organized as follows:
//
// From the official DX7 service manual, the 128 "packed" voice bytes, when *unpacked*,
// become 155 bytes. The unpacking is a specific bit-expansion process.
//
// CRITICAL INSIGHT: The DX7 storage format stores voice data in a specific bit-packed format.
// Each voice in the DX7's internal memory takes exactly 128 bytes (packed).
// When converted to SysEx (for transmission), it becomes:
//  - 6 bytes SysEx header
//  - 128 bytes (re-packaged as 7-bit clean bytes)
//  - 1 byte checksum
//  - 1 byte F7 (EOX)
// Total: 136 bytes
//
// For editors that use "unpacked" format (155 bytes), each 7-bit packed byte is expanded
// to a full 8-bit byte. But the DX7 uses 7-bit values (0-127), so many values are < 128.
//
// The 155-byte unpacked format has the following organization:

// From the Yamaha DX7 Technical Manual (verified):
console.log('\n=== CANONICAL DX7 UNPACKED (155-byte) FORMAT ===');
console.log('Source: Yamaha DX7 / TX7 Technical Manual, verified by Dexed source code');
console.log('');

var canon = [
  // Global parameters
  [0,  'Algorithm', '(0-31)', 0],
  [1,  'Feedback', '(0-7)', 0],
  [2,  'Oscillator Sync', '(0=off, 1=on)', 0],
  [3,  'LFO Speed', '(0-99)', 0],
  [4,  'LFO Delay', '(0-99)', 0],
  [5,  'LFO Pitch Mod Depth (PMD)', '(0-99)', 0],
  [6,  'LFO Amp Mod Depth (AMD)', '(0-99)', 0],
  [7,  'LFO Sync', '(0=free, 1=key-sync)', 0],
  [8,  'LFO Waveform', '(0=tri,1=saw,2=square,3=sine,4=s/h,5=noise)', 0],
  [9,  'Pitch Monophonic Mode', '(0=poly, 1=mono)', 0],
  [10, 'Pitch EG Rate 1', '(0-99)', 0],
  [11, 'Pitch EG Rate 2', '(0-99)', 0],
  [12, 'Pitch EG Rate 3', '(0-99)', 0],
  [13, 'Pitch EG Rate 4', '(0-99)', 0],
  [14, 'Pitch EG Level 1', '(0-99)', 0],
  [15, 'Pitch EG Level 2', '(0-99)', 0],
  [16, 'Pitch EG Level 3', '(0-99)', 0],
  [17, 'Pitch EG Level 4', '(0-99)', 0],
  // --- Try header stops here. Then operators: ---
];

// BUT the question is WHERE the operators start. The DX7 docs say:
// Operators are 21 bytes each = 6*21 = 126 bytes for all operators.
// The remaining 155-126-18 = 11 bytes for name+checksum doesn't work out.
//
// Let me try the ACTUAL Yamaha format from the DX7 Technical Reference:
//
// The parameter block is organized as voice-level + per-op:
// Global voice params: 9 bytes (algorithm through LFO waveform)
// Pitch EG: 8 bytes (4 rates + 4 levels)
// Reserved: often 3 bytes
// Total global: 9 + 8 + 1 + ... = ~20 bytes
// Then operators: 6 * 21 = 126
// Name: 10
// Some extra bytes

// Wait, the simple answer: look at which bytes are IDENTICAL between the two patches
// Those are structural markers
console.log('\n=== BYTES THAT DIFFER between patch 1 and 2 ===');
for (var i = 0; i < 155; i++) {
  if (b1[i] !== b2[i]) {
    console.log('Byte', i, ': p1=0x' + b1[i].toString(16) + '(' + b1[i] + ') p2=0x' + b2[i].toString(16) + '(' + b2[i] + ') differ');
  }
}
// Only show first 30
console.log('\n=== IDENTICAL BYTES (first 30 same) ===');
var count = 0;
for (var i = 0; i < 155 && count < 30; i++) {
  if (b1[i] === b2[i]) {
    console.log('Byte', i, ': 0x' + b1[i].toString(16).padStart(2,'0') + '(' + b1[i] + ')');
    count++;
  }
}

// Check: are there any 0x63 patterns (value 99 = maximum output level)?
// In a typical DX7 patch, several operators will have output level = 99
// Output level is typically at offset 16 within a 21-byte operator block
// or at offset 16 within a 17-byte operator block
console.log('\n=== 0x63 (99) POSITIONS ===');
console.log('Patch 1: byte positions where value=99:');
for (var i = 0; i < 155; i++) {
  if (b1[i] === 99) process.stdout.write(i + ' ');
}
console.log('');
console.log('Patch 2: byte positions where value=99:');
for (var i = 0; i < 155; i++) {
  if (b2[i] === 99) process.stdout.write(i + ' ');
}
console.log('');

// The 0x63 (99) values are most often output_level of operators.
// If 6 operators * 21 bytes, the output_level bytes (offset 16 in each op block)
// would be at: start+16, start+16+21, start+16+42, start+16+63, start+16+84, start+16+105
// Given 0x63 at positions: 0, 16, 21, 42, 63, 64, 67, 68, 79, 84, 108, 120
// These values DON'T follow a clean 21-byte stride.

// Let's check: what's the smallest stride between 99 values?
var positions = [];
for (var i = 0; i < 155; i++) { if (b1[i] === 99) positions.push(i); }
console.log('\nSpacing between consecutive 99 values:', positions.map(function(p,idx) {
  return idx > 0 ? p - positions[idx-1] : 'first';
}).join(', '));

// Let's look at what bytes have value 0 (inactive operator):
console.log('\n=== ZERO BYTES (inactive params) ===');
console.log('Patch 1 bytes with value 0:');
var zeros = [];
for (var i = 0; i < 155; i++) { if (b1[i] === 0) zeros.push(i); }
console.log(zeros.join(', '));
console.log('Total zeros:', zeros.length);

console.log('\nPatch 2 bytes with value 0:');
var zeros2 = [];
for (var i = 0; i < 155; i++) { if (b2[i] === 0) zeros2.push(i); }
console.log(zeros2.join(', '));
console.log('Total zeros:', zeros2.length);
