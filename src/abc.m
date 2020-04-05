disp("Running abc.m...");

function y = make_decay1(x)
  y = 0.5 ^ (x / 20000);
endfunction

function y = make_decay2(x)
  y = 0.5 ^ (x / 10000);
endfunction

function playSound(vector, bit_rate)
  ## silence = zeros(1, bit_rate);
  ## player = audioplayer ([silence, vector], bit_rate, 16);
  player = audioplayer (vector, bit_rate, 16);
  play (player);
  while(isplaying(player))
  endwhile
endfunction

function y = puretone(BIT_RATE, seconds, frequency, phase_shift=0)
  y = sinewave(BIT_RATE * seconds, BIT_RATE/frequency, phase_shift);
endfunction

function y = createharmonics(BIT_RATE, DURATION, fundamental)
  decay1 = arrayfun(@make_decay1, [1 : BIT_RATE * DURATION]);
  decay2 = arrayfun(@make_decay2, [1 : BIT_RATE * DURATION]);
  f1 = puretone(BIT_RATE, DURATION, fundamental); % .* decay1;
  f2 = puretone(BIT_RATE, DURATION, fundamental * 2); % .* decay2;
  f3 = puretone(BIT_RATE, DURATION, fundamental * 3); % .* decay2;
  f4 = puretone(BIT_RATE, DURATION, fundamental * 4); % .* decay2;
  f5 = puretone(BIT_RATE, DURATION, fundamental * 5); % .* decay2;
  f6 = puretone(BIT_RATE, DURATION, fundamental * 6); % .* decay2;
  y = f1; ## (f1 + f2 + f3 + f4 + f5 + f6) / 6;
endfunction

function m = maxfft(BIT_RATE, X, checkFreq)
  L = length(X);
  Y = fft(X);
  P2 = abs(Y / L);
  P1 = P2(1:(L / 2) + 1);
  ## P1(2:end-1) = 2 * pi * P1(2:end-1);

  [maxVal, maxIndex] = max(P1);

  ## maxFreq = maxIndex * BIT_RATE / length(X);

  maxVal
  maxIndex

  P1(maxIndex) = 0;

  [maxVal2, maxIndex2] = max(P1);

  maxVal2
  maxIndex2
  
  m = maxIndex; # maxFreq;
endfunction

function v = ffttest(BIT_RATE, X, checkFreq)
  L = length(X);
  Y = fft(X);
  P2 = abs(Y / L);
  P1 = P2(1:(L / 2) + 1);
  checkIndex = checkFreq * L / BIT_RATE;
  [checkVal, checkIndex2] = max(P1(checkIndex - 5 : checkIndex + 5));
  v = checkVal;
endfunction

## location must be between 0.0 and 1.0 
function y = kink(L, location)
  k = location;
  x = (0 : L);
  y1 = x / (k * L);
  y2 = (1 - (x / L)) / (1 - k);
  y = [y1(1:k*L), y2(k*L+1:L)];
endfunction

function main()
  BIT_RATE = 44100;
  A3 = createharmonics(BIT_RATE, 0.5, 220);
  A4 = createharmonics(BIT_RATE, 0.5, 440);
  C4 = createharmonics(BIT_RATE, 0.5, 523.26);
  E4 = createharmonics(BIT_RATE, 0.5, 660);
  E3 = createharmonics(BIT_RATE, 0.5, 330);

  B4 = createharmonics(BIT_RATE, 0.5, 495);
  D3 = createharmonics(BIT_RATE, 0.5, 293.33);
  D4 = createharmonics(BIT_RATE, 0.5, 293.33 * 2);
  GS4 = createharmonics(BIT_RATE, 0.5, 415.305);

  F5 = createharmonics(BIT_RATE, 0.5, 348.84 * 2);
  D5 = createharmonics(BIT_RATE, 0.5, 293.33 * 4);
  A5 = createharmonics(BIT_RATE, 0.5, 880);

  aMinor = [A4, (C4 + E4) / 2, E3, (C4 + E4) / 2];
  eMajor = [B4, (E4 + GS4) / 2, E3, (D4 + GS4) / 2];
  dMinor = [A4, (D4 + F5) / 2, D3, (D4 + F5) / 2];

  ## v = ffttest(BIT_RATE, A3, 220)

  v = kink(100, 0.5);
  abs(fft(v))(1:10)

  ## ffttest(100, v, 10)

  ## disp("A4");
  ## ffttest(BIT_RATE, A4);

  ## disp("E3");
  ## ffttest(BIT_RATE, E3);

  ## disp("E4");
  ## ffttest(BIT_RATE, E4);

  ## song = [aMinor, eMajor, aMinor, eMajor, dMinor, aMinor, eMajor, A4, E3, A3];
  ## playSound(song, BIT_RATE)
  ## audiowrite("output.wav", song, BIT_RATE);

  v = kink(1000, 0.5); bar(abs(fft(v-mean(v)))(1:10)(2:end))

  
endfunction

main()
disp("Finished abc.m");
