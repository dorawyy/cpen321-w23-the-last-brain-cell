const { OAuth2Client } = require('google-auth-library');
const { StatusCodes } = require('http-status-codes');

// middleware to authenticate user
const authMiddleware = async (req, res, next) => {
  const authHeader = req.headers.authorization;
  let token;

  // get token
  if (authHeader.startsWith('Bearer ')) {
    token = authHeader.substring(7, authHeader.length);
  } else {
    const invalidAuthorizationHeader = new Error('No valid authorization header');
    return res.status(403).json({ error: invalidAuthorizationHeader });
  }

  const client = new OAuth2Client();
  const serverClientId = '188221629259-675olt1lscjefjkllj14cuo801r5eoqv.apps.googleusercontent.com';

  try {
    const ticket = await client.verifyIdToken({
      idToken: token,
      audience: serverClientId,
    });
    const payload = ticket.getPayload();
    const userId = payload['sub'];

    // userid is now accessible through req object
    req.userId = userId;
    console.log('User ID: ' + req.userId);

    return next();
  } catch (error) {
    // EXPLANATION NOTE: we create a custom error object here rather than using an error
    // object caught by the try/catch block
    const invalidAuthorizationHeader = new Error('No valid authorization header');
    invalidAuthorizationHeader.status = StatusCodes.FORBIDDEN;
    next(invalidAuthorizationHeader);
  }
};

module.exports = authMiddleware;