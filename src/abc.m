disp("Running abc.m...");

function y = damping(x, dampingTime, bitRate)
  y = 0.5 ^ (x / (dampingTime * bitRate));
endfunction

function y = createDamping(bitRate, duration, dampingTime)
  y = arrayfun(@(x) damping(x, bitRate, dampingTime), [1 : bitRate * duration]);
endfunction

function playSound(vector, bitRate)
  player = audioplayer (vector, bitRate, 16);
  play (player);
  while(isplaying(player))
  endwhile
endfunction

function y = puretone(bitRate, seconds, frequency, phaseShift=0)
  y = sinewave(bitRate * seconds, bitRate/frequency, phaseShift);
endfunction

function y = createtone(bitRate, duration, frequency, dampingFactors)
  y = puretone(bitRate, duration, frequency) .* dampingFactors;
endfunction

function y = createharmonics(bitRate, duration, fundamental, weights)

  dampingFactors = createDamping(bitRate, duration, 0.3);

  M = [];

  ## Build up matrix where each row is another harmonic
  for index = 1 : length(weights)
    M = [M; weights(index) * createtone(bitRate, duration, fundamental * index, dampingFactors)];
  endfor

  ## Collapse them at the end
  S = sum(M);
  
  y = S / max(S);
endfunction

function m = maxfft(bitRate, X, checkFreq)
  L = length(X);
  Y = fft(X);
  P2 = abs(Y / L);
  P1 = P2(1:(L / 2) + 1);
  ## P1(2:end-1) = 2 * pi * P1(2:end-1);

  [maxVal, maxIndex] = max(P1);

  ## maxFreq = maxIndex * bitRate / length(X);

  maxVal
  maxIndex

  P1(maxIndex) = 0;

  [maxVal2, maxIndex2] = max(P1);

  maxVal2
  maxIndex2
  
  m = maxIndex; # maxFreq;
endfunction

function v = ffttest(bitRate, X, checkFreq)
  L = length(X);
  Y = fft(X);
  P2 = abs(Y / L);
  P1 = P2(1:(L / 2) + 1);
  checkIndex = checkFreq * L / bitRate;
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

## Returns the first 10 harmonic weights for the given pluck location
function y = getHarmonicWeights(pluckLocation)
  v = kink(1000, pluckLocation);
  X = abs(fft(v - mean(v)));
  y = (X / max(X))(2:10);
endfunction

function filtertest()
  sf = 800; sf2 = sf/2;
  data=[[1;zeros(sf-1,1)],sinetone(25,sf,1,1),sinetone(50,sf,1,1),sinetone(100,sf,1,1)];
  [b,a]=butter ( 1, 50 / sf2 );
  filtered = filter(b,a,data);
endfunction

function main()
  bitRate = 44100;

  weights = getHarmonicWeights(0.15);
  duration = 2.0;
  
  A3 = createharmonics(bitRate, duration, 220, weights);

  ## A4 = createharmonics(bitRate, duration, 440, weights);
  C3 = createharmonics(bitRate, duration, 523.26 / 2, weights);
  ## C4 = createharmonics(bitRate, duration, 523.26, weights);
  ## E4 = createharmonics(bitRate, duration, 660, weights);
  E3 = createharmonics(bitRate, duration, 330, weights);
  E2 = createharmonics(bitRate, duration, 330 / 2, weights);

  ## B4 = createharmonics(bitRate, duration, 495, weights);
  ## D3 = createharmonics(bitRate, duration, 293.33, weights);
  ## D4 = createharmonics(bitRate, duration, 293.33 * 2, weights);
  ## GS4 = createharmonics(bitRate, duration, 415.305, weights);

  ## F5 = createharmonics(bitRate, duration, 348.84 * 2, weights);
  ## D5 = createharmonics(bitRate, duration, 293.33 * 4, weights);
  ## A5 = createharmonics(bitRate, duration, 880, weights);

  aMinor = [A3, (C3 + E3) / 2, E2, (C3 + E3) / 2];
  ## eMajor = [B4, (E4 + GS4) / 2, E3, (D4 + GS4) / 2];
  ## dMinor = [A4, (D4 + F5) / 2, D3, (D4 + F5) / 2];

  ## v = ffttest(bitRate, A3, 220)

  ## ffttest(100, v, 10)

  ## disp("A4");
  ## ffttest(bitRate, A4);

  ## disp("E3");
  ## ffttest(bitRate, E3);

  ## disp("E4");
  ## ffttest(bitRate, E4);

  song = [aMinor]; #, eMajor, aMinor, eMajor, dMinor, aMinor, eMajor, A4, E3, A3];
  playSound(song, bitRate)
  ## audiowrite("with_damping.wav", song, bitRate);
endfunction

##main()
filtertest()
disp("Finished abc.m");
