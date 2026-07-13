// Renders CV-shaped data (a TailoredCv from the AI or the stored ProfileDto —
// they share section fields) in the same visual style as the PDF/Word CV.

function dateRange(start, end) {
  return `${start ?? ''}–${end && end.trim() ? end : 'Present'}`
}

export default function CvPreview({ data, contact }) {
  if (!data) return null
  const skills = data.skills && Object.entries(data.skills)

  return (
    <div className="cv-preview">
      {contact && (
        <div className="cv-head">
          <div className="cv-name">{contact.fullName}</div>
          <div className="cv-contact">
            {[contact.phone, contact.email, contact.location]
              .filter(Boolean)
              .join(' | ')}
            {contact.links &&
              Object.entries(contact.links).map(([label, url]) => (
                <span key={label}>
                  {' | '}
                  <a href={url} target="_blank" rel="noreferrer">
                    {label}
                  </a>
                </span>
              ))}
          </div>
        </div>
      )}

      {data.summary && (
        <>
          <h3 className="cv-section">Summary</h3>
          <p className="cv-text">{data.summary}</p>
        </>
      )}

      {data.experiences?.length > 0 && (
        <>
          <h3 className="cv-section">Work Experience</h3>
          {data.experiences.map((exp, i) => (
            <div key={i} className="cv-exp">
              <div className="cv-exp-head">
                <span className="cv-exp-title">{exp.title}</span>
                <span className="cv-exp-company">{exp.company}</span>
                <span className="cv-exp-dates">{dateRange(exp.startDate, exp.endDate)}</span>
              </div>
              {(exp.team || exp.location) && (
                <div className="cv-exp-sub">
                  <span>{exp.team}</span>
                  <span>{exp.location}</span>
                  <span />
                </div>
              )}
              {exp.bullets?.length > 0 && (
                <ul className="cv-bullets">
                  {exp.bullets.map((b, j) => (
                    <li key={j}>{b}</li>
                  ))}
                </ul>
              )}
            </div>
          ))}
        </>
      )}

      {data.education?.length > 0 && (
        <>
          <h3 className="cv-section">Education</h3>
          <ul className="cv-bullets">
            {data.education.map((edu, i) => (
              <li key={i} className="cv-edu">
                <span>
                  <b>
                    {[edu.degree, edu.field].filter(Boolean).join(' in ')},
                  </b>{' '}
                  {edu.institution}.{edu.details ? ` ${edu.details}` : ''}
                </span>
                <span className="cv-exp-dates">{dateRange(edu.startDate, edu.endDate)}</span>
              </li>
            ))}
          </ul>
        </>
      )}

      {skills?.length > 0 && (
        <>
          <h3 className="cv-section">Technical Skills</h3>
          <ul className="cv-bullets">
            {skills.map(([category, items]) => (
              <li key={category}>
                <b className="cv-skill-cat">{category}:</b> {items.join(', ')}
              </li>
            ))}
          </ul>
        </>
      )}

      {data.projects?.length > 0 && (
        <>
          <h3 className="cv-section">Projects and Volunteer Work</h3>
          <ul className="cv-bullets">
            {data.projects.map((p, i) => (
              <li key={i}>
                <b>{p.name}:</b> {p.description}
                {p.highlights?.length > 0 ? ` ${p.highlights.join(' ')}` : ''}
              </li>
            ))}
          </ul>
        </>
      )}

      {data.certifications?.length > 0 && (
        <>
          <h3 className="cv-section">Certifications</h3>
          <ul className="cv-bullets">
            {data.certifications.map((c, i) => (
              <li key={i}>{c}</li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
