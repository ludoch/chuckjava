#!/bin/bash
# Use chuck-samples/src/main/resources/examples as default
EXAMPLES_DIR=${1:-chuck-samples/src/main/resources/examples}
TIMEOUT=3
VERBOSE=2
TOTAL=0; SUCCESS=0; SILENT=0; FAILED=0

# Use grep on the resolved directory
while read -r ck_file; do
    ((TOTAL++))
    echo -n "[$TOTAL] Testing $ck_file... "
    # run.sh already handles prefixing examples/ if needed, 
    # but here we pass the full path from the grep result.
    OUTPUT=$(./run.sh --timeout:$TIMEOUT --verbose:$VERBOSE --halt "$ck_file" 2>&1)

    if echo "$OUTPUT" | grep -q "Exception"; then
        echo "CRASH"; ((FAILED++))
    elif echo "$OUTPUT" | grep -q "RMS:"; then
        echo "SOUND"; ((SUCCESS++))
    else
        echo "SILENT"; ((SILENT++))
    fi
done < <(grep -r -l "dac" "$EXAMPLES_DIR" | grep "\.ck$" | sort)
echo "----------------------------------------------------"
echo "Total: $TOTAL | Sound: $SUCCESS | Silent: $SILENT | Crashed: $FAILED"
