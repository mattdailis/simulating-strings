disp("Started abc.m");

pkg load signal

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

  C3 = createharmonics(bitRate, duration, 523.26 / 2, weights);
  E3 = createharmonics(bitRate, duration, 330, weights);
  E2 = createharmonics(bitRate, duration, 330 / 2, weights);
  aMinor = [A3, (C3 + E3) / 2, E2, (C3 + E3) / 2];

  song = [aMinor];
  playSound(song, bitRate)

  ## Uncomment the next line to save the audio to a file
  ## audiowrite("with_damping.wav", song, bitRate);
endfunction

##main()
filtertest()
disp("Finished abc.m");
