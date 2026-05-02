var fs = require('fs');
var p = 'deluge/src/main/java/org/chuck/deluge/BridgeContract.java';
var s = fs.readFileSync(p, 'utf8');

// 1. Add ENV_STRIDE constant after ENV_PARAMS
s = s.replace(
  'public static final int ENV_PARAMS = 4;',
  'public static final int ENV_PARAMS = 4;\n  /** Envelope array stride: TRACKS \u00d7 ENV_PARAMS. One 4-element block per track row. */\n  public static final int ENV_STRIDE = TRACKS * ENV_PARAMS;'
);

// 2. Change env allocation from ENV_COUNT*ENV_PARAMS to ENV_STRIDE
s = s.replace(
  'env = new ChuckArray("float", ENV_COUNT * ENV_PARAMS);',
  'env = new ChuckArray("float", ENV_STRIDE);'
);

// 3. Change initDefaults loop to iterate TRACKS instead of ENV_COUNT
s = s.replace(
  'for (int e = 0; e < ENV_COUNT; e++) {\n      env.setFloat(e * ENV_PARAMS + 0, 0.01f);',
  'for (int e = 0; e < TRACKS; e++) {\n      env.setFloat(e * ENV_PARAMS + 0, 0.01f);'
);

// 4. Update setEnv javadoc and indexing
s = s.replace(
  '   * @param envIndex envelope index (0 to {@code ENV_COUNT-1}).',
  '   * @param envIndex envelope index (0 to {@code TRACKS-1}).'
);

fs.writeFileSync(p, s, 'utf8');
console.log('Done');
