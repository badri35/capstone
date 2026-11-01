#!/bin/bash
# Generate traffic against the service. usage: load-test.sh <url> <good|bad|mixed> [seconds]
URL="${1:-http://localhost:18080}"; MODE="${2:-mixed}"; DURATION="${3:-60}"
good='{"transactionId":"TX-%06d","sourceAccount":"ACC10000001","destinationAccount":"ACC20000002","amount":125.50,"currency":"USD"}'
bad='{"transactionId":"TX-%06d","sourceAccount":"ACC10000001","destinationAccount":"ACC10000001","amount":-5,"currency":"XYZ"}'
end=$((SECONDS + DURATION)); i=0
while [ $SECONDS -lt $end ]; do
  i=$((i + 1))
  case "$MODE" in
    good) body=$(printf "$good" $i) ;;
    bad)  body=$(printf "$bad" $i) ;;
    *)    if [ $((i % 4)) -eq 0 ]; then body=$(printf "$bad" $i); else body=$(printf "$good" $i); fi ;;
  esac
  curl -s -o /dev/null -X POST "$URL/validate" -H 'Content-Type: application/json' -d "$body"
  sleep 0.1
done
echo "sent $i requests ($MODE) to $URL"
